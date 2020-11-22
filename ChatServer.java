import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.lang.*;
import java.io.IOException;

/* FALTA FAZER O COMANDO PRIVATE E TRATAR DO OUTPUT PK EM VEZ DE IMPRIMIR NAME:MESSAGE IMPRIME NAME \N : MESSAGE , DE RESTO ESTA TUDO (MAS VERIFICA PF) */



public class ChatServer {

    // buffer pré-alocado para os dados recebidos
    static private final ByteBuffer buffer = ByteBuffer.allocate(16384);
    static List<String> list = new ArrayList<String>();
    // descodificador para texto que chegue -- assumir UTF-8
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();
    

    static public void main(String args[]) throws Exception {
	// analisar porta introduzida na linha de comando
	int port = Integer.parseInt(args[0]);

	try {
	    // em vez de criar uma ServerSocket, criar uma ServerSocketChannel
	    ServerSocketChannel ssc = ServerSocketChannel.open();

	    // colocá-la como non-blocking, para que possamos usar select
	    ssc.configureBlocking(false);

	    ServerSocket ss = ssc.socket();
	    InetSocketAddress isa = new InetSocketAddress(port);
	    ss.bind(isa);
	    
	    Selector selector = Selector.open();
	    ssc.register(selector, SelectionKey.OP_ACCEPT);
	    System.out.println("Listening on port " + port);

	    while (true) {

		int num = selector.select();
		if (num == 0) {
		    continue;
		}

		Set<SelectionKey> keys = selector.selectedKeys();
		Iterator<SelectionKey> it = keys.iterator();
		boolean canReadName = false;
		while (it.hasNext()) {
		    SelectionKey key = it.next();

		    // que tipo de atividade é?
		    if (key.isAcceptable()) {

			// é uma conexão futura. Registar a socket com
			// o Selector para que possamos ouvir input nela
			Socket s = ss.accept();
			System.out.println("Got connection from " + s);

			// assegurar que seja non-blocking, para que possamos usar um selector nela
			SocketChannel sc = s.getChannel();
			sc.configureBlocking(false);
			ClientInfo info = new ClientInfo("anonimous",s.getPort(),"init","none");
			// registá-la com o selector, para leitura
			sc.register(selector, SelectionKey.OP_READ,info);

		    } else if (key.isReadable()) {

			// -----------------------------------------------------------------------
			SocketChannel sc = null;

			try {

			    // dados futuros numa conexão -- processá-los
			    sc = (SocketChannel)key.channel();
			    
			    StringBuilder sb = new StringBuilder();

			    buffer.clear();

			    int read = 0;
			    while ((read = sc.read(buffer)) > 0) {
				buffer.flip(); // preparar o buffer
				byte[] bytes = new byte[buffer.limit()];
				buffer.get(bytes);
				sb.append(new String(bytes));
				buffer.clear();

			    }

			    String msg;
			    
			    if(read < 0) { // se estivermos no fim da stream a conexão é fechada
				        msg = " left the chat.\n";
				        sc.close();
			    } else { // caso contrário, a msg prossegue
				msg = sb.toString();
			    }

			    if(!isComand(msg,key,selector,sc)){
				//System.out.println("Received a Command");
				ClientInfo inf = (ClientInfo)key.attachment();
				if((inf.state).equals("init") || (inf.state).equals("outside")){
				    msg = "ERROR\n";
				    send(msg,key,selector);
				}
				else if(msg.startsWith("/")){
					msg = "ERROR\n";
					send(msg,key,selector);
				    }
				else{
				    msg = inf.name + ": " + sb.toString();
				    sendGroup(msg,inf.chatGroup,key,selector);
				}				
			    }
			    else {
				System.out.println("Received a Command");
			    }

			} catch (IOException ie) {
			    // em exceção, remove-se este canal do selector
			    key.cancel();

			    try {
				sc.close();
			    } catch (IOException ie2) {
				System.out.println(ie2);
			    }
			    System.out.println("Closed " + sc);
			}
		    }
		    it.remove();
		}

		// removemos as chaves selecionadas, porque já lidámos com elas
		keys.clear();

	    }

	} catch (IOException ie) {
	    System.err.println(ie);
	}
    }

    public static boolean isComand(String message,SelectionKey key,Selector selector,SocketChannel sc)throws Exception{
	ClientInfo info = (ClientInfo)key.attachment();
	if(message.startsWith("/nick")){

	    String name = message.substring(message.indexOf("k")+2);
	    if(!list.contains(name)){
		list.add(name);
		String msg = "OK\n";
		
		if((info.name).equals("anonimous"))
		    info.name = name;
		else{
		    if((info.state).equals("inside")){
			String antigo = info.name;
			info.name = name;
			String msg1 = antigo+" changes name to "+name;
			String chatGroup = info.chatGroup;
			sendGroup(msg1,chatGroup,key,selector);
		    }
		}
		if((info.state).equals("init")){
		    info.state = "outside";
		}
		key.attach(info);
		send(msg,key,selector);
	    }
	    else{
		String msg = "ERROR\n";
		send(msg,key,selector);
	    }
	    return true;
	}
	if(message.startsWith("/join")){ //entrar numa sala
	    String sala = message.substring(message.indexOf("n")+2);
	    String msg;
	    if((info.state).equals("inside"))
		{
		    msg = "LEFT "+info.name+"\n";
		    sendGroup(msg,info.chatGroup,key,selector);
		    info.chatGroup = sala;
		    msg = "OK\n";
		    send(msg,key,selector);
		}
	    else if((info.state).equals("outside"))
		{
		    info.state = "inside";
		    info.chatGroup = sala;
		    msg = "OK\n";
		    send(msg,key,selector);
		}
	    else {
		msg = "ERROR\n";
		send(msg,key,selector);
	    }
	    return true;
	}
        if(message.startsWith("/leave")){ // sair da sala
	    String msg = "BYE\n";
	    send(msg,key,selector);
	    if((info.state).equals("inside")){
		msg = "LEFT "+info.name+"\n";
		sendGroup(msg,info.chatGroup,key,selector);
		info.state = "outside";
	    }
	    else {
		msg = "ERROR\n";
		send(msg,key,selector);
	    }
	    return true;
	}
	if(message.startsWith("/bye")){ // sair da coneccao
	    String msg = "BYE\n";
	    send(msg,key,selector);
	    if((info.state).equals("inside")){
		msg = "LEFT "+info.name+"\n";
		sendGroup(msg,info.chatGroup,key,selector);
	    }
	    removeConnection(key,sc);
	    return true;
	}

	if(message.startsWith("/private")){ //este ainda nao esta a funcionar
	    
	    return true;
	}
	
	return false;
    }


    public static void send(String message,SelectionKey key,Selector selector) throws Exception{
       ByteBuffer msgBuf = ByteBuffer.wrap(message.getBytes());
	for(SelectionKey k : selector.keys()) {
	   
	    if( k == key) {
		SocketChannel sch = (SocketChannel)k.channel();
		sch.write(msgBuf);
		msgBuf.clear();
	    }

	}
    }

    public static void sendGroup(String message,String chatGroup,SelectionKey key,Selector selector) throws Exception{
	ByteBuffer msgBuf = ByteBuffer.wrap(message.getBytes());
	for(SelectionKey k : selector.keys()) {
	    ClientInfo info = (ClientInfo)k.attachment();
	    if(k.isValid() && k.channel() instanceof SocketChannel && k != key) {
		if((info.chatGroup).equals(chatGroup) && (info.state).equals("inside")){
		    SocketChannel sch = (SocketChannel)k.channel();
		    sch.write(msgBuf);
		    msgBuf.clear();
		}
	    }
	}
    }

    public static void removeConnection(SelectionKey key,SocketChannel sc ) throws Exception{
	key.cancel();

	Socket s = null;
	try{
	    s = sc.socket();
	    System.out.println("Closing connection to "+s );
	    s.close();
	}catch( IOException ie){
	    System.err.println("Error closing socket "+s+": "+ie );
	}

	try {
	    sc.close();
	} catch( IOException ie2 ) { System.out.println( ie2 ); }

	System.out.println( "Closed "+sc );
    }
   
}
