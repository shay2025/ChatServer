import java.io.*;
import java.nio.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    Socket clientSocket;
    DataOutputStream outToServer;
    String server;
    int port;
    BufferedReader inFromServer;
    LinkedList<String> commands;
    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    
    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
	frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocus();
            }
	    });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
	this.server = server;
	this.port = port;
	// cria socket do cliente e estabelece conexão com servidor
	this.clientSocket = new Socket(server, port);
	this.commands = new LinkedList<String>(Arrays.asList("/nick", "/join", "/bye", "/leave", "/priv"));

    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
	// cria stream de saida associada à socket
	outToServer = new DataOutputStream(clientSocket.getOutputStream());

	String firstWord;
	if (message.contains(" "))
	    firstWord = message.substring(0, message.indexOf(" "));
	else
	    firstWord = message.substring(0, message.length());

	// caso do escape
	if (firstWord.charAt(0) == '/' && !commands.contains(firstWord))
	    message = "/" + message;
	
	// '\n' é o terminador de mensagem
	message += '\n';
	// envia linha (pedido) ao servidor
	outToServer.write(message.getBytes());
    }

    
    // Método principal do objecto
    public void run() throws IOException {

	// se conexão estabelecida
	try {

	    // cria stream de entrada associada à socket
	    inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
	
	    // lê linha (resposta) do servidor
	    String msgServer = inFromServer.readLine();
	    // enquanto a msg não for nula
	    // isto é, enquanto o servidor não fechar a conexão
	    while (msgServer != null) {

		// processa a msg vinda do servidor
		// e imprime no formato respetivo dependendo do tipo de msg
		printMessage(processMsg(msgServer));

		msgServer = inFromServer.readLine();
	    
	    }

	    outToServer.close();
	    clientSocket.close();   
	    
	} catch (IOException e) {

	    // se o servidor estiver offline não faz nada e falha
	    System.err.println(e);
	    return;
	    
	}
	
    }

    // mensagem que coloca no formato amigável
    public final String processMsg(String message) {
	
	if (message.startsWith("MESSAGE")) {

	    String content = message.substring(message.indexOf(" ") + 1);
	    String sender = content.substring(0, content.indexOf(" "));
	    String msg = content.substring(content.indexOf(" ") + 1);

	    message = sender + ": " + msg + "\n";
	    
	} else if (message.startsWith("NEWNICK")) {

	    String content = message.substring(message.indexOf(" ") + 1);
	    String oldName = content.substring(0, content.indexOf(" "));
	    String newName = content.substring(content.indexOf(" ") + 1, content.length());

	    message = "(" + oldName + " mudou de nome para " + newName + ")\n";
	    
	} else if (message.startsWith("JOINED")) {

	    String name = message.substring(message.indexOf(" ") + 1);
	    message = "(" + name + " entrou na sala)\n";
	    
	} else if (message.startsWith("LEFT")) {

	    String name = message.substring(message.indexOf(" ") + 1);
	    message = "(" + name + " saiu da sala)\n";
	    
	} else if (message.startsWith("PRIVATE")) {

	    String content = message.substring(message.indexOf(" ") + 1);
	    String name = content.substring(0, content.indexOf(" "));
	    String msg = content.substring(content.indexOf(" ") + 1);

	    message = "(privado)" + name + ": " + msg + "\n"; 
	    
	} else if (message.equals("OK"))
	    message = "(Ação efetuada com sucesso)\n";
	else if (message.equals("ERROR"))
	    message = "(Ação efetuada com insucesso)\n";
	else
	    message = message + "\n";
	return message;
	
    }


    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
