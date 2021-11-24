package TimeServer.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ServerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

    private Bootstrap bootstrap;
//    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public ServerHandler(){
        bootstrap = new Bootstrap();
//        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(1);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        final String str = (String) msg;

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ForwardInitializer(ctx));
        ChannelFuture connectFuture = bootstrap.connect("192.168.0.222", 8080).sync();
        connectFuture.addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                //发送请求
                ChannelFuture channelFuture = (ChannelFuture) future;
                if (channelFuture.isSuccess()) {
                } else {
                    log.warn("连接失败：{}", channelFuture.channel().remoteAddress());
                }
            }
        });
        connectFuture.channel().closeFuture().sync();
    }
}
class ForwardInitializer extends ChannelInitializer<SocketChannel>{

    private ChannelHandlerContext serverHandlerContext;
    public ForwardInitializer(ChannelHandlerContext serverHandlerContext){
        this.serverHandlerContext = serverHandlerContext;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast("forward-decoder",new HttpRequestDecoder());
        ch.pipeline().addLast("forward-encoder",new HttpResponseEncoder());
        ch.pipeline().addLast("forward-handler",new ForwardHandler(serverHandlerContext));
    }
}
class ForwardHandler extends ChannelInboundHandlerAdapter{
    private static final Logger log = LoggerFactory.getLogger(ForwardHandler.class);

    private ChannelHandlerContext serverHandlerContext;
    public ForwardHandler(ChannelHandlerContext serverHandlerContext){
        this.serverHandlerContext = serverHandlerContext;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info(ctx.channel().remoteAddress()+" 连接成功");
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.GET,"1");
        ctx.writeAndFlush(request);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        HttpResponse response = (DefaultHttpResponse) msg;
        serverHandlerContext.writeAndFlush(response.decoderResult().toString());
    }
}

class TestClient{
    public static void main(String[] args) {

        Bootstrap bootstrap = new Bootstrap();
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
//        EventLoopGroup workerGroup = new NioEventLoopGroup(1);

        bootstrap.group(bossGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new HttpRequestEncoder());
                        ch.pipeline().addLast(new HttpResponseDecoder());
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter(){
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                System.out.println("握手成功");
                                DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.GET,"/1.html");
                                request.headers().set("Host","192.168.0.222:8080");
                                ctx.writeAndFlush(request);
                            }
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                System.out.println(msg);
                            }
                        });
                    }
                });
        try {
            ChannelFuture future = bootstrap.connect("192.168.0.222",8080).sync();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            bossGroup.shutdownGracefully();
        }
    }
}
