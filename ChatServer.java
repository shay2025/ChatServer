import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.lang.*;
import java.io.IOException;

class ClientInfo {

    int port;
    String name;
    String state;
    String chatGroup;
    ByteBuffer bufferUser;

    public ClientInfo(String name, int port, String state, String chatGroup){
	this.name = name;
	this.port = port;
	this.state = state;
	this.chatGroup = chatGroup;
	bufferUser = ByteBuffer.allocate(20000);
    }
    
}

public class ChatServer {

    // lista que contém o nome dos clientes da sala de chat
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
	    // obtem porta do cliente
	    InetSocketAddress isa = new InetSocketAddress(port);
	    // associa a socket com o endereço local isa
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
			// inicialização dos dados do cliente
			ClientInfo info = new ClientInfo("anonimous", s.getPort(), "init", "none");
      			// registá-la com o selector, para leitura e associar ao selector o cliente
			sc.register(selector, SelectionKey.OP_READ, info);

		    } else if (key.isReadable()) {

			SocketChannel sc = null;

			try {

			    // dados futuros numa conexão -- processá-los
			    sc = (SocketChannel)key.channel();
			    int ok = processInput(sc, key, selector);

			    // se o input for diferente de um <ENTER>
			    // a conexão está morta e portanto é removida do selector e fechada
			    if (ok == -1) {
				
				key.cancel();

				Socket s = null;
				try {
				    s = sc.socket();
				    System.out.println("Closing connection to " + s);
				    s.close();
				} catch(IOException ie) {
				    System.err.println("Error closing socket " + s + ": " + ie);
				}
				
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

    public static int processInput(SocketChannel sc, SelectionKey key, Selector selector) throws Exception {

	// vai buscar a informação do cliente que está conectada com a key dada como argumento
	ClientInfo user = (ClientInfo)key.attachment();
	ByteBuffer bufferUser = user.bufferUser;

	/*
	  -> o buffer já está em modo de escrita inicialmente;
	  -> escreve na última posição de escrita, ou seja, vai escrever a seguir ao que havia anteriormente;
	 */
	sc.read(bufferUser); // começa a escrever no buffer de imediato pois assume que já está em modo de escrita
	bufferUser.flip(); // o buffer passa para o modo de leitura

	// se não houver dados, fechar conexão | retorna código de erro
	if (bufferUser.limit() == 0) return -1;
	
	// descodificar a mensagem do buffer
	String msg = decoder.decode(bufferUser).toString();
	// input só pode ser processado quando se fizer <ENTER>
	if (msg.charAt(msg.length() - 1) != '\n') {
	    // reescreve no buffer o que tinha lido anteriormente, ou seja, faz a bufferização dos pedaços de msg
	    bufferUser.clear(); // reinicia a posição de escrita no buffer (leitura -> escrita)
	    byte[] bytes = msg.getBytes(StandardCharsets.UTF_8); // codifica a msg
	    bufferUser = bufferUser.put(bytes); // coloca a msg novamente no buffer
	    return 0;
	}

	// lê buffer desde o início
	bufferUser.rewind();
	msg = decoder.decode(bufferUser).toString();
	
	int i=0;
	while (i < msg.length()) {

	    String command = "";
	    for (int j=i; j<msg.length(); j++) {
		command += msg.charAt(j);
		if (msg.charAt(j) == '\n') {
		    i = j+1;
		    break;
		}
	    }
	    
	    // ou seja, é uma mensagem
	    if (!isComand(command, key, selector, sc)){

		String MSG;
				
		// se o cliente ainda não tiver sido inicializado
		// ou estiver fora da sala ou for um comando
		if((user.state).equals("init") || (user.state).equals("outside")) {
				    
		    MSG = "ERROR\n";
		    // notifica o cliente que a ação deu erro
		    send(MSG, key, selector);
					
		} else {

		    bufferUser.clear();
		    MSG = "MESSAGE " + user.name + " " + command;
		    // envia msg a todos os utilizadores
		    sendAll(MSG, user.chatGroup, key, selector);
				    
		}
				
	    } else { // recebeu um comando
				
		System.out.println("Received a Command");
				
	    }

	}

	bufferUser.clear(); // limpa o buffer para receber uma nova msg
	return 1;
			    
    }

    // função que trata dos comandos
    public static boolean isComand(String message, SelectionKey key, Selector selector, SocketChannel sc) throws Exception {
	// vai buscar a informação do cliente que está conectada com a key dada como argumento
	ClientInfo info = (ClientInfo)key.attachment();
	
	if(message.startsWith("/nick")){

	    String name = message.substring(message.indexOf("k") + 2); // vai buscar o nome inserido
	    name = name.substring(0, name.length() - 1); // remove o new line a mais

	    // se ainda não houver nenhum cliente com o nome escolhido
	    if(!list.contains(name)){
		
		list.add(name);
		String msg = "OK\n";
		
		if((info.name).equals("anonimous"))
		    info.name = name;
		
		else {

		    // se o cliente quiser mudar de nome
		    if((info.state).equals("inside")){
			
			String antigo = info.name;
			info.name = name;
			String msg1 = "NEWNICK " + antigo + " " + name + "\n";
			String chatGroup = info.chatGroup;
			// envia um alerta a todos os outros utilizadores de que o cliente mudou de nome
			sendGroup(msg1, chatGroup, key, selector);
			
		    }
		    
		}
		
		if((info.state).equals("init")){
		    info.state = "outside";
		}

		// anexar um novo objeto à key atual
		key.attach(info);
		// notifica o cliente que a ação foi feita sem problemas
		send(msg, key, selector);
		
	    } else {
		
		String msg = "ERROR\n";
		// notifica o cliente que a ação resultou num erro
		send(msg, key, selector);
		
	    }
	    
	    return true;
	    
	}
	
	if(message.startsWith("/join")){ //entrar numa sala
	    
	    String sala = message.substring(message.indexOf("n")+2); // vai buscar nome da sala
	    String msg;
	    
	    if((info.state).equals("inside")) {
		
		    msg = "LEFT " + info.name + "\n";
		    // notifica todos os utilizadores de que o cliente saiu da sala
		    sendGroup(msg, info.chatGroup, key, selector);
		    // muda a sala do utilizador para a que ele indicou agora
		    info.chatGroup = sala;
		    msg = "OK\n";
		    // notifica o cliente que a ação foi feita sem problemas
		    send(msg, key, selector);
		    
	    } else if((info.state).equals("outside")) {

		info.state = "inside";
		info.chatGroup = sala;
		msg = "JOINED " + info.name + "\n";
		// notifica todos os utilizadores de que entrou um novo utilizador na sala
		sendGroup(msg, info.chatGroup, key, selector);
		msg = "OK\n";
		// notifica o cliente que a ação foi feita sem problemas
		send(msg,key,selector);
		    
	    } else {

		// notifica o cliente que a ação resultou num erro
		msg = "ERROR\n";
		send(msg,key,selector);
		
	    }
	    
	    return true;
	    
	}
	
        if(message.startsWith("/leave")){ // sair da sala
	    
	    String msg = "BYE\n";
	    send(msg, key, selector);
	    
	    if((info.state).equals("inside")){ // se o cliente estiver dentro da sala

		// notifica todos os utilizadores de que o cliente saiu da sala
		msg = "LEFT " + info.name + "\n";
		sendGroup(msg,info.chatGroup,key,selector);
		// atualiza o estado do cliente
		info.state = "outside";
		
	    } else { // senão estiver dentro de um sala dá erro
		
		msg = "ERROR\n";
		send(msg,key,selector);
		
	    }
	    
	    return true;
	    
	}
	
	if(message.startsWith("/bye")){ // sair da coneccao

	    // envia a msg de BYE para o cliente que efetuou o comando
	    String msg = "BYE\n";
	    send(msg, key, selector);
	    
	    if((info.state).equals("inside")){ // se o cliente estiver dentro da sala

		// notifica todos os utilizadores da saída do cliente
		msg = "LEFT " + info.name + "\n";
		sendGroup(msg, info.chatGroup, key, selector);
		
	    }

	    // remove a conexão que o cliente tem com o servidor
	    removeConnection(key, sc);
	    
	    return true;
	    
	}

	if(message.startsWith("/priv")){ //este ainda nao esta a funcionar

	    String arg = message.substring(message.indexOf(" ") + 1); // argumentos do comando /priv
	    String name = arg.substring(0, arg.indexOf(" "));
	    String contMsg = arg.substring(arg.indexOf(" ") + 1, arg.length() - 1);
	    String msg = "PRIVATE " + info.name + " " + contMsg + "\n";
	    ByteBuffer msgBuf = ByteBuffer.wrap(msg.getBytes());
		
	    for(SelectionKey k : selector.keys()) {
		if (!list.contains(name)) break;
		    
		// vai buscar a informação do cliente associado à chave k
		ClientInfo info2 = (ClientInfo)k.attachment();

		// se a chave for válida e o nome do user associada à chave atual for igual ao destinatário
		// e esse destinatário pertence à mesma sala que o user que enviou a mensagem
		// e encontra-se dentro de um sala, então transmite a msg para o destinatário
		if (k.isValid() && k.channel() instanceof SocketChannel && k != key && (info2.name).equals(name) &&
		    (info2.chatGroup).equals(info.chatGroup) && (info2.state).equals("inside")) {
		    SocketChannel sch = (SocketChannel)k.channel();
		    sch.write(msgBuf);
		    msgBuf.clear();
		}
		
	    }
	    
	    return true;
	    
	}
	
	return false;
    }

    // função que dada uma msg envia-a para o cliente que tenha a key dada como argumento
    public static void send(String message, SelectionKey key, Selector selector) throws Exception {
       ByteBuffer msgBuf = ByteBuffer.wrap(message.getBytes());
       
	for(SelectionKey k : selector.keys()) {
	  
	    if(k == key) {
		SocketChannel sch = (SocketChannel)k.channel();
		sch.write(msgBuf);
		msgBuf.clear();
	    }

	}
    }

    // função que envia uma dada msg para todos os utilizadores do chatGroup
    public static void sendGroup(String message, String chatGroup, SelectionKey key, Selector selector) throws Exception {
	ByteBuffer msgBuf = ByteBuffer.wrap(message.getBytes());
	
	for(SelectionKey k : selector.keys()) {
	    // vai buscar a informação do cliente associado à chave k
	    ClientInfo info = (ClientInfo)k.attachment();
	    
	    if(k.isValid() && k.channel() instanceof SocketChannel && k != key) {
		// se o chatGroup do cliente atual corresponder à do que emitiu a msg
		// e o cliente atual estiver dentro de um chatGroup
		if((info.chatGroup).equals(chatGroup) && (info.state).equals("inside")){
		    SocketChannel sch = (SocketChannel)k.channel();
		    sch.write(msgBuf);
		    msgBuf.clear();
		}
		
	    }
	    
	}
    }

    public static void sendAll(String message, String chatGroup, SelectionKey key, Selector selector) throws Exception {
	ByteBuffer msgBuf = ByteBuffer.wrap(message.getBytes());
	
	for(SelectionKey k : selector.keys()) {
	    // vai buscar a informação do cliente associado à chave k
	    ClientInfo info = (ClientInfo)k.attachment();
	    
	    if(k.isValid() && k.channel() instanceof SocketChannel) {
		// se o chatGroup do cliente atual corresponder à do que emitiu a msg
		// e o cliente atual estiver dentro de um chatGroup
		if((info.chatGroup).equals(chatGroup) && (info.state).equals("inside")){
		    SocketChannel sch = (SocketChannel)k.channel();
		    sch.write(msgBuf);
		    msgBuf.clear();
		}
		
	    }
	    
	}
    }

    // função que fecha a conexão de um cliente ao servidor
    public static void removeConnection(SelectionKey key, SocketChannel sc) throws Exception{
	
	key.cancel();

	Socket s = null;
	
	try {
	    
	    s = sc.socket();
	    System.out.println("Closing connection to " + s);
	    s.close();
	    
	} catch( IOException ie){
	    System.err.println("Error closing socket " + s + ": " + ie);
	}

	try {
	    sc.close();
	} catch( IOException ie2 ) { System.out.println(ie2); }

	System.out.println("Closed " + sc);
    }
   
}
