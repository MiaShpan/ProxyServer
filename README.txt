*This assignment was done under the computer networks course, some of the code was written by the instructor as part of the assignment.

Sockspy.java
The class contains the main function - it creates a MultiThreadedProxyServer object and runs it.

MultiThreadedProxyServer.java
The class represents our multi threaded proxy server.
It listens on port 8080 and creates a ProxyServerThread that handles the connection with each connected client.

ProxyServerThread.java
The class represents the thread that handles the client connection:
It reads and validates the client's connect request, sends ack message accordingly,
tries to connect to the desired destination and starts two ReadWriteThread to handle data relay if necessary

ReadWriteThread.java
The class represents a single thread that handles data relay: each thread receives a socket to read from and
a socket to write to and all it does is transfer data from one socket to the other.
In addition, it parses the Authorization header and prints the user and password if necessary.
