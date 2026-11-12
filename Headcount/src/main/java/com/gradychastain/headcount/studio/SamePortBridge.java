package com.gradychastain.headcount.studio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.kyori.adventure.key.Key;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lets Studio share the Minecraft port. Paper calls us for every new connection (its ChannelInitializeListener hook);
 * we put a small handler at the front of the connection that looks at the first bytes. A Minecraft client always
 * starts with a packet length and then a zero byte (or 0xFE for a legacy ping), a browser with an HTTP method like
 * "GET ". Minecraft connections go on untouched. Browser connections are piped to Studio's own HTTP server, which
 * listens on a loopback port that nothing outside the machine can reach.
 */
final class SamePortBridge {
    private static final Key KEY = Key.key("headcount", "studio");
    private static final String[] METHODS = {"GET ", "POST ", "HEAD ", "PUT ", "OPTIONS ", "DELETE ", "PATCH "};
    /** The loopback port of each pipe -> the browser's real address, so sign-in limits see the real address. */
    private static final Map<Integer, InetSocketAddress> REAL = new ConcurrentHashMap<>();

    private final Logger log;
    private final int targetPort;
    private Method removeListener;
    private Class<?> holder;

    SamePortBridge(Logger log, int targetPort) {
        this.log = log;
        this.targetPort = targetPort;
    }

    /** Hooks into Paper. Returns false (and logs why) when this server doesn't offer the hook. */
    boolean install() {
        try {
            holder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
            Class<?> listenerType = Class.forName("io.papermc.paper.network.ChannelInitializeListener");
            Object listener = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{listenerType}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "afterInitChannel" -> {
                        try {
                            ((Channel) args[0]).pipeline().addFirst("headcount_studio", new Sniffer());
                        } catch (RuntimeException e) {
                            log.log(Level.FINE, "Studio couldn't watch a new connection", e);
                        }
                        return null;
                    }
                    case "hashCode" -> { return System.identityHashCode(proxy); }
                    case "equals" -> { return proxy == args[0]; }
                    case "toString" -> { return "Headcount Studio port sharing"; }
                    default -> { return null; }
                }
            });
            if ((boolean) holder.getMethod("hasListener", Key.class).invoke(null, KEY)) {
                holder.getMethod("removeListener", Key.class).invoke(null, KEY);
            }
            holder.getMethod("addListener", Key.class, listenerType).invoke(null, KEY, listener);
            removeListener = holder.getMethod("removeListener", Key.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            log.warning("This server doesn't let plugins share the Minecraft port (" + e + "). Set studio.port in config.yml to a port of its own.");
            return false;
        }
    }

    void uninstall() {
        if (removeListener == null) return;
        try {
            removeListener.invoke(null, KEY);
        } catch (ReflectiveOperationException e) {
            log.log(Level.FINE, "Couldn't remove Studio's port sharing", e);
        }
        removeListener = null;
    }

    /** The browser's real address for a connection that came through a pipe, or the address itself. */
    static InetSocketAddress realAddress(InetSocketAddress seen) {
        if (seen == null || !seen.getAddress().isLoopbackAddress()) return seen;
        InetSocketAddress real = REAL.get(seen.getPort());
        return real != null ? real : seen;
    }

    /** -1: not HTTP; 0: can't tell yet; 1: HTTP. */
    static int classify(ByteBuf buf) {
        int n = buf.readableBytes();
        boolean possible = false;
        for (String m : METHODS) {
            int len = Math.min(n, m.length());
            boolean match = true;
            for (int i = 0; i < len && match; i++) match = buf.getByte(buf.readerIndex() + i) == (byte) m.charAt(i);
            if (!match) continue;
            if (n >= m.length()) return 1;
            possible = true;
        }
        return possible ? 0 : -1;
    }

    /** Sits first on every new connection until it knows what the connection is. */
    private final class Sniffer extends ChannelInboundHandlerAdapter {
        private ByteBuf pending;
        private Pipe pipe;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (pipe != null) {
                if (msg instanceof ByteBuf b) pipe.toStudio(b);
                return;
            }
            if (!(msg instanceof ByteBuf in)) {
                ctx.fireChannelRead(msg);
                return;
            }
            if (pending == null) pending = ctx.alloc().buffer(Math.max(16, in.readableBytes()));
            pending.writeBytes(in);
            in.release();
            int kind = classify(pending);
            if (kind == 0 && pending.readableBytes() < 16) return;
            ByteBuf data = pending;
            pending = null;
            if (kind <= 0) {
                // Minecraft (or anything else): hand it on and step out of the way for good.
                ctx.fireChannelRead(data);
                if (ctx.pipeline().context(this) != null) ctx.pipeline().remove(this);
                return;
            }
            // A browser. Minecraft's read timeout would cut long downloads, so it goes; the rest of Minecraft's
            // handlers never see a byte, because nothing is passed on from here.
            if (ctx.pipeline().get("timeout") != null) ctx.pipeline().remove("timeout");
            pipe = new Pipe(ctx);
            pipe.toStudio(data);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (pipe != null) pipe.close();
            if (pending != null) {
                pending.release();
                pending = null;
            }
            super.channelInactive(ctx);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            if (pending != null) {
                pending.release();
                pending = null;
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (pipe == null) {
                ctx.fireExceptionCaught(cause);
                return;
            }
            pipe.close();
            ctx.close();
        }
    }

    /** One browser connection piped to Studio's loopback HTTP server, one thread each way. */
    private final class Pipe {
        private static final byte[] CLOSE = new byte[0];
        private static final long HIGH = 1 << 20, LOW = 256 << 10;
        private final ChannelHandlerContext ctx;
        private final BlockingQueue<byte[]> toStudio = new LinkedBlockingQueue<>();
        private final AtomicLong queued = new AtomicLong();
        private volatile Socket socket;
        private volatile boolean closed;

        Pipe(ChannelHandlerContext ctx) {
            this.ctx = ctx;
            Thread.ofVirtual().name("Headcount Studio pipe").start(this::run);
        }

        void toStudio(ByteBuf b) {
            byte[] bytes = new byte[b.readableBytes()];
            b.readBytes(bytes);
            b.release();
            if (closed) return;
            toStudio.add(bytes);
            if (queued.addAndGet(bytes.length) > HIGH) ctx.channel().config().setAutoRead(false);
        }

        private void run() {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress("127.0.0.1", targetPort), 3000);
                socket = s;
                SocketAddress remote = ctx.channel().remoteAddress();
                if (remote instanceof InetSocketAddress isa) REAL.put(s.getLocalPort(), isa);
                Thread.ofVirtual().name("Headcount Studio pipe").start(this::back);
                OutputStream out = s.getOutputStream();
                while (!closed) {
                    byte[] bytes = toStudio.take();
                    if (bytes == CLOSE) break;
                    out.write(bytes);
                    out.flush();
                    if (queued.addAndGet(-bytes.length) < LOW && !ctx.channel().config().isAutoRead()) ctx.channel().config().setAutoRead(true);
                }
            } catch (IOException | InterruptedException e) {
                // the browser or Studio went away
            } finally {
                close();
            }
        }

        /** Studio's replies back to the browser, written from the front of the pipeline so no Minecraft encoder touches them. */
        private void back() {
            try {
                InputStream in = socket.getInputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    while (!ctx.channel().isWritable() && ctx.channel().isActive()) Thread.sleep(5);
                    if (!ctx.channel().isActive()) break;
                    ctx.writeAndFlush(Unpooled.copiedBuffer(buf, 0, n));
                }
            } catch (IOException | InterruptedException e) {
                // closed
            } finally {
                if (ctx.channel().isActive()) ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(f -> ctx.close());
                close();
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            toStudio.add(CLOSE);
            Socket s = socket;
            if (s != null) {
                REAL.remove(s.getLocalPort());
                try {
                    s.close();
                } catch (IOException ignored) {
                    // already closed
                }
            }
        }
    }
}
