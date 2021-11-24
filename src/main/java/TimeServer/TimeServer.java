package TimeServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

public class TimeServer {

    private static final Logger log = LoggerFactory.getLogger(TimeServer.class);

    public static void main(String[] args) {

        try {
            ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.bind(new InetSocketAddress(8080));
            ssc.configureBlocking(false);
            final Selector selector = Selector.open();
            final SelectionKey key = ssc.register(selector, SelectionKey.OP_ACCEPT);
            final boolean isStop = false;

            new Thread(new Runnable() {
                @Override
                public void run() {

                    while(!isStop){
                        try {
                            if(selector.select() > 0){
                               Set<SelectionKey> selectionKeys = selector.selectedKeys();
                                Iterator<SelectionKey> selectionKeyIterator = selectionKeys.iterator();
                               while(selectionKeyIterator.hasNext()){
                                   SelectionKey sk = selectionKeyIterator.next();
                                   selectionKeyIterator.remove();

                                   if(sk.isAcceptable()){
                                       ServerSocketChannel serverSocketChannel = (ServerSocketChannel) sk.channel();
//                                       serverSocketChannel.configureBlocking(false);
                                       SocketChannel  sc = serverSocketChannel.accept();
                                       sc.configureBlocking(false);
                                       if(sc.finishConnect()){
                                            log.info(sc.getRemoteAddress()+" connected");
                                            sc.register(selector,SelectionKey.OP_READ);
                                       }
                                   }else if(sk.isReadable()){
                                       ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                                       SocketChannel sc = (SocketChannel)sk.channel();
                                       sc.configureBlocking(false);
                                       int byteCount = sc.read(byteBuffer);
                                       if(byteCount > 0){
                                           byte[] bytes = new byte[byteCount];
                                           byteBuffer.flip();
                                           byteBuffer.get(bytes);
                                           String content = new String(bytes, Charset.forName("utf-8"));
                                           log.info("接收客户端发送消息："+content);

                                           String nowStr = new Date().toString();
                                           sc.write(ByteBuffer.wrap(nowStr.getBytes()));
                                       }
                                   }
                               }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            },"ServerSocket-Thread").start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
