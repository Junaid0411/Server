package net;

import java.util.HashMap;
import java.util.Random;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

import net.packet.CreateGameSession;
import net.packet.IceEffect;
import net.packet.JoinGameSession;
import net.packet.LeftGameSession;
import net.packet.Login;
import net.packet.PlayerInfo;
import net.packet.SessionInfo;
import net.packet.StartGame;
import net.packet.TerminateSession;
import net.packet.Winner;

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
		} catch (Exception e) {
			e.printStackTrace();
		}

		server.addListener(new Listener() {
			public void connected(Connection connection) {

			}

			public void received(Connection connection, Object object) {

				if (object instanceof Login) {
					Login packet = (Login) object;
					packet.id = connection.getID();
					server.sendToTCP(connection.getID(), packet);
				}

				if (object instanceof TerminateSession) {
					TerminateSession packet = (TerminateSession) object;
					sessions.get(packet.token).getPlayerByID(connection.getID()).playing = false;

					if (isDeleteable(packet)) {
						sessions.remove(packet.token);
					}

				}

				if (object instanceof LeftGameSession) {
					LeftGameSession packet = (LeftGameSession) object;
					GameSession session = sessions.get(packet.token);
					session.removePlayerByID(packet.playerID);
					
					if (session.getPlayers().size() <= 0) {
						sessions.remove(packet.token);
					} else {						
						notifyAllPlayers(session);
					}
				}

				if (object instanceof CreateGameSession) {
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

				if (object instanceof JoinGameSession) {
					// Get users token
					JoinGameSession packet = (JoinGameSession) object;
					// Checking if the token is correct
					if (sessions.get(packet.token) == null || !sessions.containsKey(packet.token)) {
						// ErrorPacket invalidPacket = new ErrorPacket();
						// invalidPacket.invalidToken = true;
						packet.errorToken = true; // token is wrong
						server.sendToTCP(connection.getID(), packet);
						// Checking if session is in progress
					} else if (sessions.get(packet.token).getHasStarted()) {
						packet.joinedLate = sessions.get(packet.token).getHasStarted(); // session has started
						server.sendToTCP(connection.getID(), packet);
					} else {
						sessions.get(packet.token).addPlayer(connection.getID(), packet.name);
						packet.joinedLate = sessions.get(packet.token).getHasStarted(); // session hasn't started
						notifyAllPlayers(sessions.get(packet.token));

						server.sendToTCP(connection.getID(), packet);
					}
				}

				if (object instanceof StartGame) {
					StartGame packet = (StartGame) object;
					if (sessions.get(packet.token) != null) {
						GameSession session = sessions.get(packet.token);
						packet.playerIDs = session.getPlayerIDs();
						packet.playerNames = session.getPlayerNames();
						session.setHasStarted(true); // Host has started the session
						for (Integer connectionID : session.getPlayerIDs()) {
							server.sendToTCP(connectionID, packet);
						}
					}
					System.out.println(sessions.get(packet.token));
				}

				if (object instanceof PlayerInfo) {
					PlayerInfo packet = (PlayerInfo) object;
					if (sessions.get(packet.token) != null) {
						GameSession session = sessions.get(packet.token);
						for (Integer connectionID : session.getPlayerIDs()) {
							server.sendToTCP(connectionID, packet);
						}
					}
				}

				if (object instanceof Winner) {
					Winner packet = (Winner) object;

					if (sessions.get(packet.token) != null) {
						GameSession session = sessions.get(packet.token);
						String winnerName = "";
						for (Player p : session.getPlayers()) {
							if (p.getID() == packet.playerID)
								winnerName = p.getName();
						}

						if (session.setWinner(winnerName)) {
							packet.winnerName = winnerName;
							for (Integer connectionID : session.getPlayerIDs()) {
								server.sendToTCP(connectionID, packet);
							}
						}
					}
				}

				if (object instanceof IceEffect) {
					IceEffect packet = (IceEffect) object;
					if (sessions.get(packet.token) != null) {
						GameSession session = sessions.get(packet.token);
						for (Integer connectionID : session.getPlayerIDs()) {
							if (connectionID != packet.playerID)
								server.sendToTCP(connectionID, packet);
						}
					}
				}

			}
		});
	}

	private boolean isDeleteable(TerminateSession packet) {
		for (Player player : sessions.get(packet.token).getPlayers())
			if (player.playing)
				return false;
		return true;
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
		} while (sessions.get(saltStr) != null);

		return saltStr;
	}

	public static void main(String[] args) {
		// Log.set(Log.LEVEL_DEBUG);
		new MPServer();
	}
}
