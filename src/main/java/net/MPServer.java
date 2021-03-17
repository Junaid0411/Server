package net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;

import net.packet.CreateGameSession;
import net.packet.JoinGameSession;
import net.packet.Login;
import net.packet.PlayerPosition;
import net.packet.SessionInfo;
import net.packet.StartGame;

public class MPServer {

	private final static int TOKEN_LENGTH = 5;
	public Server server;
	public HashMap<String, GameSession> sessions;
		
	public MPServer() {
		server = new Server();
		server.start();
		
		Network.register(server);
		
		sessions = new HashMap<>();
				
		try {
			server.bind(Network.TCP_PORT, Network.UDP_PORT);
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		server.addListener(new Listener() {
			public void connected(Connection connection) {
				
			}
			
			public void received(Connection connection, Object object) {
				
				if(object instanceof Login) {
					Login packet = (Login) object;
					packet.id = connection.getID();
					server.sendToTCP(connection.getID(), packet);
				}
				
				if(object instanceof CreateGameSession) {
					// make a token and store the game ID to that connection
					// its 4am check this over when im awake
					CreateGameSession packet = (CreateGameSession) object;
					String token = generateGameToken();
					packet.token = token;
					GameSession session = new GameSession(token, packet.mapPath);
					session.addPlayer(connection.getID(), packet.name);
					session.setHost(connection.getID());
					sessions.put(token, session);
					
					server.sendToTCP(connection.getID(), packet);
					notifyAllPlayers(session);
				}
				
				if(object instanceof JoinGameSession) {
					// get his token and see if exists
					JoinGameSession packet = (JoinGameSession) object;

						if(sessions.get(packet.token) != null) {
							GameSession session = sessions.get(packet.token);
							System.out.println(session.getToken() + " == " + packet.token);
							System.out.println("There are currently " + session.getPlayers().size() + " players in the room.");
							
							session.addPlayer(connection.getID(), packet.name);
							notifyAllPlayers(session);
							
							System.out.println("There are currently " + session.getPlayers().size() + " players in the room.");
							server.sendToTCP(connection.getID(), packet);
						}
						
						// TODO
						// if it does put him into the same lobby
						// if not its an invalid token
				}
				
				if (object instanceof StartGame) {
					StartGame packet = (StartGame) object;
					if(sessions.get(packet.token) != null) {
						GameSession session = sessions.get(packet.token);
						packet.playerIDs = session.getPlayerIDs();
						packet.playerNames = session.getPlayerNames();
						for (Integer connectionID : session.getPlayerIDs()) {
							server.sendToTCP(connectionID, packet);
						}
					}
				}
				
				if (object instanceof PlayerPosition) {
					PlayerPosition packet = (PlayerPosition) object;
					if (sessions.get(packet.token) != null) {
						GameSession session = sessions.get(packet.token);
						for (Integer connectionID : session.getPlayerIDs()) {
							server.sendToTCP(connectionID, packet);
						}
					}
				}
			}
		});	
	}
	
	private void notifyAllPlayers(GameSession session) {
		SessionInfo packet = new SessionInfo();
		packet.playerIDs = session.getPlayerIDs();
		packet.playerNames = session.getPlayerNames();
		packet.mapPath = session.getMapPath();
		packet.token = session.getToken();
		for (Integer connectionID : session.getPlayerIDs()) {
			packet.playerID = connectionID;
			server.sendToTCP(connectionID, packet);
		}
	}
	
	private String generateGameToken() {
		String saltStr;
		do {
	        String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
	        StringBuilder salt = new StringBuilder();
	        Random rnd = new Random();
	        while (salt.length() < TOKEN_LENGTH) { // length of the random string.
	            int index = (int) (rnd.nextFloat() * SALTCHARS.length());
	            salt.append(SALTCHARS.charAt(index));
	        }
	        saltStr = salt.toString();	        
		} while(sessions.get(saltStr) != null);
		
		return saltStr;
	}
	
	public static void main(String[] args) {
//		Log.set(Log.LEVEL_DEBUG);
		new MPServer();
	}
}
