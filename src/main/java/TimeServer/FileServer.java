package TimeServer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.io.File;
import java.io.RandomAccessFile;

public class FileServer {

    private static final String DEFAULT_URL = "E:/netty";

    public static void main(String[] args) {


        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workGroup = new NioEventLoopGroup(1);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup,workGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast("http-decoder",new HttpRequestDecoder());
                        ch.pipeline().addLast("aggration",new HttpObjectAggregator(65535));
                        ch.pipeline().addLast("http-encoder",new HttpResponseEncoder());
                        ch.pipeline().addLast("http-chunked",new ChunkedWriteHandler());
                        ch.pipeline().addLast("fileHandler",new FileHandler(DEFAULT_URL));
                    }
                });
        try {
            ChannelFuture channelFuture = bootstrap.bind(8080).sync();
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }

    }
}

class FileHandler extends SimpleChannelInboundHandler<FullHttpRequest>{

    private String url;
    public FileHandler(String url){
        this.url = url;
    }
    public FileHandler(){}
    private static final String pattern = "(/[a-zA-z0-9]{0,}){0,}";
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        DecoderResult result = request.decoderResult();
        if(!result.isSuccess()){
            sendError(ctx,HttpResponseStatus.BAD_REQUEST);
            return;
        }

        if (request.method() != HttpMethod.GET) {
            sendError(ctx,HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        String uri = request.uri();
        if (uri == null || !uri.matches(pattern)){
            sendError(ctx,HttpResponseStatus.NO_CONTENT);
            return;
        }

        String location = url + uri;
        File file = new File(location);
        if(!file.exists() || file.isHidden()){
            sendError(ctx,HttpResponseStatus.NO_CONTENT);
            return;
        }

        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);
        if(file.isFile()){
            httpResponse.headers().set("Content-Type","text/html;charset=UTF-8");
            RandomAccessFile randomAccessFile = new RandomAccessFile(file,"r");
            httpResponse.headers().set("Content-Length",randomAccessFile.length());
            byte[] bytes = new byte[(int)randomAccessFile.length()];
            randomAccessFile.read(bytes);
            httpResponse.content().writeBytes(bytes);
            ctx.writeAndFlush(httpResponse);
        }else if(file.isDirectory()){

        }
    }

    private void sendError(ChannelHandlerContext ctx,HttpResponseStatus status) {
        DefaultHttpResponse defaultHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        ctx.writeAndFlush(defaultHttpResponse);
    }
}
