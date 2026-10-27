package outboundgroup

import (
	"sync"
	"sync/atomic"
	"time"

	"github.com/metacubex/mihomo/common/buf"
	C "github.com/metacubex/mihomo/constant"
)

type smartConn struct {
	C.Conn
	group         *Smart
	node, site    string
	start         time.Time
	sampled       atomic.Bool
	stopped       atomic.Bool
	written       atomic.Bool
	deadlineMu    sync.Mutex
	readDeadline  time.Time
	firstDeadline time.Time
	armed         bool
}

func newSmartConn(conn C.Conn, group *Smart, node, site string, start time.Time) C.Conn {
	return &smartConn{Conn: conn, group: group, node: node, site: site, start: start}
}

func (c *smartConn) sample(n int, err error) {
	if c.stopped.Load() {
		return
	}
	if (n > 0 || (err != nil && c.written.Load())) && c.sampled.CompareAndSwap(false, true) {
		c.group.observe(c.node, c.site, false, time.Since(c.start), n > 0)
		c.deadlineMu.Lock()
		if c.armed {
			_ = c.Conn.SetReadDeadline(c.readDeadline)
			c.armed = false
		}
		c.deadlineMu.Unlock()
	}
}

// Only bound the wait for the FIRST response after sending. Afterwards the
// upstream fast copy path and caller's original deadline are restored.
func (c *smartConn) arm() {
	c.deadlineMu.Lock()
	defer c.deadlineMu.Unlock()
	if !c.armed && !c.sampled.Load() && c.written.Load() {
		deadline := time.Now().Add(c.group.timeout)
		c.firstDeadline = deadline
		if !c.readDeadline.IsZero() && c.readDeadline.Before(deadline) {
			deadline = c.readDeadline
		}
		_ = c.Conn.SetReadDeadline(deadline)
		c.armed = true
	}
}

func (c *smartConn) Read(p []byte) (int, error) {
	c.arm()
	n, err := c.Conn.Read(p)
	c.sample(n, err)
	return n, err
}

func (c *smartConn) ReadBuffer(buffer *buf.Buffer) error {
	c.arm()
	before := buffer.Len()
	err := c.Conn.ReadBuffer(buffer)
	c.sample(buffer.Len()-before, err)
	return err
}

func (c *smartConn) Write(p []byte) (int, error) {
	if len(p) > 0 {
		c.written.Store(true)
	}
	n, err := c.Conn.Write(p)
	if err != nil {
		c.sample(0, err)
	} else {
		c.arm()
	}
	return n, err
}

func (c *smartConn) WriteBuffer(buffer *buf.Buffer) error {
	if buffer.Len() > 0 {
		c.written.Store(true)
	}
	err := c.Conn.WriteBuffer(buffer)
	if err != nil {
		c.sample(0, err)
	} else {
		c.arm()
	}
	return err
}

func (c *smartConn) SetReadDeadline(deadline time.Time) error {
	c.deadlineMu.Lock()
	defer c.deadlineMu.Unlock()
	c.readDeadline = deadline
	if c.armed {
		limit := c.firstDeadline
		if deadline.IsZero() || limit.Before(deadline) {
			deadline = limit
		}
	}
	return c.Conn.SetReadDeadline(deadline)
}

func (c *smartConn) SetDeadline(deadline time.Time) error {
	if err := c.SetReadDeadline(deadline); err != nil {
		return err
	}
	return c.Conn.SetWriteDeadline(deadline)
}

func (c *smartConn) Close() error {
	c.stopped.Store(true)
	return c.Conn.Close()
}

func (c *smartConn) Upstream() any           { return c.Conn }
func (c *smartConn) ReaderReplaceable() bool { return c.sampled.Load() }
func (c *smartConn) WriterReplaceable() bool { return c.written.Load() }
