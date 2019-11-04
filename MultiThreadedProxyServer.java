import com.sun.tools.internal.ws.wsdl.document.jaxws.Exception;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MultiThreadedProxyServer {

    private  ServerSocket proxy;
    private ExecutorService threadsPool;
    private static final int NUMBER_OF_THREADS = 20;
    private static final int PROXY_SERVER_PORT = 8080;


    public MultiThreadedProxyServer(){
        try {
            proxy = new ServerSocket(PROXY_SERVER_PORT);
            threadsPool = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
        } catch (IOException e) {
            System.err.println("Can't create proxy server on port 8080");
        }
    }

    public void run(){
    while (true) {
        try {
            Socket clientSocket = proxy.accept();
            ProxyServerThread clientThread = new ProxyServerThread(clientSocket);
            threadsPool.execute(clientThread);
            } catch (IOException e) {
                System.err.println("Couldn't create client socket");
            }
        }
    }

}

