import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.lang.*;
import java.io.IOException;

public class ChatServer {

    // buffer pré-alocado para os dados recebidos
    static private final ByteBuffer buffer = ByteBuffer.allocate(16384);
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
			    
			    StringBuilder sb = new StringBuilder();

			    // coloca o buffer na posição 0 para escrita
			    buffer.clear();

			    int read = 0;
			    // enquanto não se atingir o fim do buffer (simbolizado por -1) na leitura
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

			    // ou seja, é uma mensagem
			    if (!isComand(msg, key, selector, sc)){

				// vai buscar o objeto cliente associado à key atual
				// isto é, vai buscar o comando enviado pelo cliente
				ClientInfo inf = (ClientInfo)key.attachment();
				
				// se o cliente ainda não tiver sido inicializado
				// ou estiver fora da sala ou for um comando
				if((inf.state).equals("init") || (inf.state).equals("outside")) {
				    
				    msg = "ERROR1\n";
				    // notifica o cliente que a ação deu erro
				    send(msg, key, selector);
					
				} else {

				    msg = "MESSAGE " + inf.name + " " + sb.toString();
				    // envia msg a todos os utilizadores
				    sendGroup(msg, inf.chatGroup, key, selector);
				    
				}
				
			    } else { // recebeu um comando
				
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

    // função que trata dos comandos
    public static boolean isComand(String message, SelectionKey key, Selector selector, SocketChannel sc)throws Exception{

	// vai buscar a informação do cliente que está conectada com a key dada como argumento
	ClientInfo info = (ClientInfo)key.attachment();
	
	if(message.startsWith("/nick")){

	    String name = message.substring(message.indexOf("k") + 2); // vai buscar o nome inserido
	    name = name.substring(0, name.length() - 1);

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
			String msg1 = "NEWNICK " + antigo + " " + name;
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
	    send(msg,key,selector);
	    
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
	    String contMsg = arg.substring(arg.indexOf(" ") + 1, arg.length() - 2);
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
    public static void send(String message, SelectionKey key, Selector selector) throws Exception{
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
    public static void sendGroup(String message, String chatGroup, SelectionKey key, Selector selector) throws Exception{
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
