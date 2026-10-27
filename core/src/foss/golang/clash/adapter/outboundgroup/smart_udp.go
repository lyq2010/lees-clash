package outboundgroup

import (
	"context"
	"net"
	"sync"
	"sync/atomic"
	"time"

	C "github.com/metacubex/mihomo/constant"
)

func (s *Smart) ListenPacketContext(ctx context.Context, m *C.Metadata) (C.PacketConn, error) {
	if s.closed.Load() {
		return nil, net.ErrClosed
	}
	if s.noUDP {
		return nil, C.ErrNotSupport
	}
	proxies, site := s.GetProxies(true), smartSiteKey(m)
	candidates := s.candidates(proxies, site, true)
	s.maybeProbe(proxies)
	err := errSmartEmpty
	for _, p := range candidates {
		if p == nil || ctx.Err() != nil {
			break
		}
		attempt, cancel := context.WithTimeout(ctx, s.timeout)
		pc, dialErr := p.ListenPacketContext(attempt, m)
		cancel()
		if dialErr == nil {
			pc.AppendToChains(s)
			return &smartPacketConn{PacketConn: pc, group: s, node: p.Name(), site: site}, nil
		}
		err = dialErr
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		s.observe(p.Name(), site, true, 0, false)
	}
	if ctx.Err() != nil {
		return nil, ctx.Err()
	}
	return nil, err
}

type smartPacketConn struct {
	C.PacketConn
	group      *Smart
	node, site string
	sent       atomic.Int64
	sampled    atomic.Bool
	stopped    atomic.Bool
	mu         sync.Mutex
	deadline   time.Time
}

func (c *smartPacketConn) sample(n int, err error) {
	if c.stopped.Load() {
		return
	}
	sent := c.sent.Load()
	if sent == 0 || (n == 0 && err == nil) {
		return
	}
	if c.sampled.CompareAndSwap(false, true) {
		c.group.observe(c.node, c.site, true, time.Since(time.Unix(0, sent)), n > 0)
		c.mu.Lock()
		_ = c.PacketConn.SetReadDeadline(c.deadline)
		c.mu.Unlock()
	}
}

func (c *smartPacketConn) WriteTo(p []byte, addr net.Addr) (int, error) {
	c.sent.CompareAndSwap(0, time.Now().UnixNano())
	c.arm()
	n, err := c.PacketConn.WriteTo(p, addr)
	if err != nil {
		c.sample(0, err)
	}
	return n, err
}

func (c *smartPacketConn) arm() {
	c.mu.Lock()
	defer c.mu.Unlock()
	if sent := c.sent.Load(); sent != 0 && !c.sampled.Load() {
		deadline := time.Unix(0, sent).Add(c.group.timeout)
		if !c.deadline.IsZero() && c.deadline.Before(deadline) {
			deadline = c.deadline
		}
		_ = c.PacketConn.SetReadDeadline(deadline)
	}
}

func (c *smartPacketConn) SetReadDeadline(deadline time.Time) error {
	c.mu.Lock()
	c.deadline = deadline
	if sent := c.sent.Load(); sent != 0 && !c.sampled.Load() {
		limit := time.Unix(0, sent).Add(c.group.timeout)
		if deadline.IsZero() || limit.Before(deadline) {
			deadline = limit
		}
	}
	err := c.PacketConn.SetReadDeadline(deadline)
	c.mu.Unlock()
	return err
}

func (c *smartPacketConn) SetDeadline(deadline time.Time) error {
	if err := c.SetReadDeadline(deadline); err != nil {
		return err
	}
	return c.PacketConn.SetWriteDeadline(deadline)
}

func (c *smartPacketConn) Close() error {
	c.stopped.Store(true)
	return c.PacketConn.Close()
}

func (c *smartPacketConn) ReadFrom(p []byte) (int, net.Addr, error) {
	n, addr, err := c.PacketConn.ReadFrom(p)
	c.sample(n, err)
	return n, addr, err
}

func (c *smartPacketConn) WaitReadFrom() ([]byte, func(), net.Addr, error) {
	data, put, addr, err := c.PacketConn.WaitReadFrom()
	c.sample(len(data), err)
	return data, put, addr, err
}

func (c *smartPacketConn) Upstream() any           { return c.PacketConn }
func (c *smartPacketConn) ReaderReplaceable() bool { return c.sampled.Load() }
func (c *smartPacketConn) WriterReplaceable() bool { return c.sent.Load() != 0 }
