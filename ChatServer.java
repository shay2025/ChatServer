import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.io.IOException;

public class ChatServer {
    
    // buffer pré-alocado para os dados recebidos
    static private final ByteBuffer buffer =
	ByteBuffer.allocate(16384);

    // descodificador para texto que chegue -- assumir UTF-8
    static private final Charset charset =
	Charset.forName("UTF8");
    static private final CharsetDecoder decoder =
	charset.newDecoder();

    static public void main(String args[]) throws Exception {
	// analisar porta introduzida na linha de comando
	int port = Integer.parseInt(args[0]);

	try {
	    // em vez de criar uma ServerSocket, criar uma ServerSocketChannel
	    ServerSocketChannel ssc = ServerSocketChannel.open();

	    // colocá-la como non-blocking, para que possamos usar select
	    ssc.configureBlocking(false);

	    // obter a socket connectada a este canal, e ligá-la à porta que se encontra à escuta
	    ServerSocket ss = ssc.socket();
	    InetSocketAddress isa = new InetSocketAddress(port);
	    ss.bind(isa);

	    // criar um novo Selector para seleção
	    Selector selector = Selector.open();

	    // registar o ServerSocketChannel, para que possamos ouvir futuras conexões
	    ssc.register(selector, SelectionKey.OP_ACCEPT);
	    System.out.println("Listening on port " + port);

	    while (true) {
		// verifcar se houve alguma atividade -- seja uma futura conexão ou dados numa conexão existente
		int num = selector.select();

		// se não tivemos nenhuma atividade, continuar com o ciclo e esperar novamente
		if (num == 0) {
		    continue;
		}

		// obter as chaves correspondentes à atividade detetada que houve
		// e processar cada uma
		Set<SelectionKey> keys = selector.selectedKeys();
		Iterator<SelectionKey> it = keys.iterator();
		boolean canReadName = false;
		while (it.hasNext()) {
		    // obter a chave que representa um dos bits da atividade I/O
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

			// registá-la com o selector, para leitura
			sc.register(selector, SelectionKey.OP_READ);
			
		    } else if (key.isReadable()) {

			// --------------------------------------------------------------------------------
			
			SocketChannel sc = null;

			try {

			    // dados futuros numa conexão -- processá-los
			    sc = (SocketChannel)key.channel();

			    // gera uma sequência mutável de caracteres, útil quando há várias threads
			    StringBuilder sb = new StringBuilder(); 

			    buffer.clear();
			    
			    int read = 0;
			    while ((read = sc.read(buffer)) > 0) { // enquanto não for atingido o fim da stream
				
				buffer.flip(); // preparar o buffer
				byte[] bytes = new byte[buffer.limit()];
				buffer.get(bytes);
				sb.append(new String(bytes));
				buffer.clear();
				
			    }
			    
			    String msg;
			    if(read < 0) { // se estivermos no fim da stream a conexão é fechada
				
				msg = key.attachment() + " left the chat.\n";
				sc.close();
				
			    } else { // caso contrário, a msg prossegue
				
				msg = key.attachment() + ": " + sb.toString();
				
			    }

			    // difusão da mensagem a todos os clientes ativos
			    ByteBuffer msgBuf = ByteBuffer.wrap(msg.getBytes());
			    for(SelectionKey k : selector.keys()) {

				if(k.isValid() && k.channel() instanceof SocketChannel && k != key) {
				    SocketChannel sch = (SocketChannel)k.channel();
				    sch.write(msgBuf);
				    msgBuf.rewind();
				}
				
			    }
			    
			    // --------------------------------------------------------------------------------
			    
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
		}

		// removemos as chaves selecionadas, porque já lidámos com elas
		keys.clear();
		
	    }
	    
	} catch (IOException ie) {
	    System.err.println(ie);
	}
    }
}
