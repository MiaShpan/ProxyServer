import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class ProxyServerThread extends Thread {

    private Socket clientSocket;
    private Socket destinationSocket;
    private InputStream messagesFromClient;
    private OutputStream messagesToClient;
    private ReadWriteThread clientThread;
    private ReadWriteThread destThread;
    private byte[] clientMessage;
    private InetAddress destinationIPAddress;
    private CountDownLatch countDownLatch;
    private static final byte VERSION = (byte)4;
    private static final int MESSAGE_SIZE_IN_BYTES = 8;
    private static final byte REQUEST_GRANTED = (byte)90;
    private static final byte REQUEST_FAILED = (byte)91;
    private static final String SUCCESSFUL_CONNECTION = "Successful connection from %s to %s";
    private static final String CONNECTION_ERROR = "Connection Error: %s";
    private static final String CLOSING_CONNECTION = "Closing connection from %s";

    public ProxyServerThread(Socket clientSocket){
        this.clientSocket = clientSocket;
        this.clientMessage = new byte[MESSAGE_SIZE_IN_BYTES];
        this.countDownLatch = new CountDownLatch(1);
        setInputOutputStreams();
    }

    @Override
    public void run() {
        try {
            setTimer();
            readClientConnectMessage();
            connectDestination();
            sendAckMessage(REQUEST_GRANTED, clientMessage);
            startDatRelay();
            countDownLatch.await();
        } catch (Exception e){
            printConnectionErrorMessage(e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void setInputOutputStreams(){
        try {
            messagesFromClient = clientSocket.getInputStream();
            messagesToClient = clientSocket.getOutputStream();
        } catch (IOException e) {
            printConnectionErrorMessage("Can't get the client's input/output stream");
            closeConnection();
        }
    }

    private void readClientConnectMessage() throws Exception{
        try {
            messagesFromClient.read(clientMessage);
            checkInputValidity();
            while (messagesFromClient.read() != 0) {
                continue;
            }
        } catch (SocketTimeoutException e){
            throw new Exception("Timeout!");
        } catch (IOException e) {
            throw new Exception("Can't read from the client's input stream");
        }
    }

    private void checkInputValidity() throws Exception{
        if(clientMessage[0] != VERSION){
            sendAckMessage(REQUEST_FAILED, clientMessage);
            throw new Exception("while parsing request: Unsupported SOCKS protocol version (got "+ clientMessage[0] +")");
        }
    }

    private void connectDestination() throws Exception{
        byte[] ipAddressArray = Arrays.copyOfRange(clientMessage,4,8);
        int port = Byte.toUnsignedInt(clientMessage[2])<<8 | Byte.toUnsignedInt(clientMessage[3]);

        try {
            destinationIPAddress = InetAddress.getByAddress(ipAddressArray);
            destinationSocket = new Socket(destinationIPAddress, port);
            printSuccessfulConnection();
        } catch (UnknownHostException e) {
            sendAckMessage(REQUEST_FAILED, clientMessage);
            throw new Exception("The destination's host is unknown");
        } catch (IOException e) {
            sendAckMessage(REQUEST_FAILED, clientMessage);
            throw new Exception("Can't connect to destination");
        }
    }

    private void sendAckMessage(byte requestStatus, byte[] clientMessage) throws Exception{
        byte[] ackMessage = new byte[MESSAGE_SIZE_IN_BYTES];
        ackMessage[0] = 0;
        ackMessage[1] = requestStatus;
        for(int i = 2; i<clientMessage.length; i++) {
            ackMessage[i] = clientMessage[i];
        }
        try {
            messagesToClient.write(ackMessage);
            messagesToClient.flush();
        } catch (IOException e1) {
            throw new Exception("Can't write ack to the client's output stream");
        }
    }

    private void printSuccessfulConnection(){
        System.err.println(String.format(SUCCESSFUL_CONNECTION, getSocketAddressString(clientSocket) ,getSocketAddressString(destinationSocket)));
    }

    static String getSocketAddressString(Socket socket){
        StringBuilder address = new StringBuilder();
        address.append(socket.getInetAddress().toString());
        address.append(":");
        address.append(socket.getPort());
        return address.toString().replaceAll("/", "");
    }

    private void setTimer() throws Exception{
        try {
            clientSocket.setSoTimeout(5000);
        } catch (SocketException e) {
            throw new Exception("Can't set socket timeout");
        }
    }

    private void startDatRelay(){
        clientThread = new ReadWriteThread(clientSocket, destinationSocket, countDownLatch);
        destThread = new ReadWriteThread(destinationSocket, clientSocket, countDownLatch);
        clientThread.start();
        destThread.start();
    }

    private void closeConnection(){
        printCloseConnectionMessage();
        try {
            clientSocket.close();
            if(destinationSocket != null){
                destinationSocket.close();
            }
        } catch (IOException e1) {
            // not handling
        }
    }

    private void printCloseConnectionMessage(){
        StringBuilder closeConnectionMessage = new StringBuilder();
        closeConnectionMessage.append(String.format(CLOSING_CONNECTION, getSocketAddressString(clientSocket)));
        if(destinationSocket != null){
            closeConnectionMessage.append(" to ");
            closeConnectionMessage.append(getSocketAddressString(destinationSocket));
        }
        System.err.println(closeConnectionMessage.toString().replaceAll("/", ""));
    }

    private void printConnectionErrorMessage(String message){
        System.err.println(String.format(CONNECTION_ERROR, message));
    }
}
