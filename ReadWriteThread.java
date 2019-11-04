import java.io.*;
import java.lang.reflect.Array;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReadWriteThread extends Thread {
    private Socket readFromSocket;
    private Socket writeToSocket;
    private InputStream readFromStream;
    private OutputStream writeToStream;
    private CountDownLatch countDownLatch;
    private byte[] buffer;
    private boolean isFirstBufferInCurrentMessage = true;
    private static final String NEW_LINE = System.lineSeparator();
    private static final int BUFFER_SIZE = 512;
    private static final String PASSWORD_FOUND = "Password Found! http://%s@%s";
    private static final String CONNECTION_ERROR = "Connection Error: %s";

    public ReadWriteThread(Socket readFrom, Socket writeTo, CountDownLatch countDownLatch) {
        this.readFromSocket = readFrom;
        this.writeToSocket = writeTo;
        this.countDownLatch = countDownLatch;
        this.buffer = new byte[BUFFER_SIZE];
    }

    @Override
    public void run() {
        try {
            setStreams();
            readAndWrite();
        } catch (SocketTimeoutException e){
            printConnectionErrorMessage("Timeout!");
        } catch (Exception e) {
            if(countDownLatch.getCount() > 0){
                printConnectionErrorMessage(e.getMessage());
            }
        } finally {
            countDownLatch.countDown();
        }
    }

    private void setStreams() throws Exception{
        try
        {
            readFromStream = readFromSocket.getInputStream();
            writeToStream = writeToSocket.getOutputStream();
        } catch (IOException e) {
            throw new Exception("Can't get the sockets' input/output streams");
        }

    }

    private void printUsernameAndPassword(String message){
        Pattern pattern = Pattern.compile("GET.*\r\nHost: (.*)\r\nAuthorization: Basic (.*==)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.lookingAt()) {
            String host = matcher.group(1);
            String userDetails = matcher.group(2);
            String details = new String(Base64.getDecoder().decode(userDetails));

            System.err.println(String.format(PASSWORD_FOUND, details, host));
        }
    }

    private void printConnectionErrorMessage(String message){
        System.err.println(String.format(CONNECTION_ERROR, message));
    }

    private void readAndWrite() throws Exception{
        int numOfReadBytes;
        try {
            while ((numOfReadBytes = readFromStream.read(buffer)) != -1) {
                if(numOfReadBytes > 0){
                    writeToOutputStream(numOfReadBytes);
                }
            }
        } catch (SocketTimeoutException e) {
            throw new Exception("Timeout!");
        } catch (SocketException e){
            throw new Exception(e.getMessage());
        } catch (IOException e) {
            throw new Exception("Can't read from " + ProxyServerThread.getSocketAddressString(readFromSocket));
        }
    }

    private void writeToOutputStream(int numOfReadBytes) throws Exception{
        boolean port80 = (writeToSocket.getPort() == 80);

        try {
            writeToStream.write(buffer, 0, numOfReadBytes);
            writeToStream.flush();

            if(port80 && isFirstBufferInCurrentMessage){
                printUsernameAndPassword(new String(Arrays.copyOf(buffer, numOfReadBytes)));
                isFirstBufferInCurrentMessage = false;
            }
        } catch (SocketTimeoutException e) {
            throw new Exception("Timeout!");
        } catch (IOException e) {
            throw  new Exception("Can't write to " + ProxyServerThread.getSocketAddressString(writeToSocket));
        }
    }
}