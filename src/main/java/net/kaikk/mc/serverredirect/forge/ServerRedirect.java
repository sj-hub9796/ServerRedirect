package net.kaikk.mc.serverredirect.forge;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.kaikk.mc.serverredirect.forge.PacketHandler.AddressMessage;
import net.kaikk.mc.serverredirect.forge.PacketHandler.VoidMessage;
import net.kaikk.mc.serverredirect.forge.commands.FallbackCommand;
import net.kaikk.mc.serverredirect.forge.commands.IfPlayerRedirectCommand;
import net.kaikk.mc.serverredirect.forge.commands.RedirectCommand;
import net.kaikk.mc.serverredirect.forge.event.PlayerRedirectEvent;
import net.kaikk.mc.serverredirect.forge.event.RedirectEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = ServerRedirect.MODID, name = ServerRedirect.NAME, version = ServerRedirect.VERSION, acceptableRemoteVersions = "*")
public class ServerRedirect {
	public static final String MODID = "serverredirect";
	public static final String NAME = "ServerRedirect";
	public static final String VERSION = "1.4.3";
	public static final Logger LOGGER = LogManager.getLogger();
	protected static final Set<UUID> players = Collections.synchronizedSet(new HashSet<>());
	@SideOnly(Side.CLIENT)
	public static volatile String redirectServerAddress;
	@SideOnly(Side.CLIENT)
	public static volatile String fallbackServerAddress;
	@SideOnly(Side.CLIENT)
	public static Thread mcThread;
	@SideOnly(Side.CLIENT)
	public static boolean connected;

	@EventHandler
	public void init(FMLInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
		FMLCommonHandler.instance().bus().register(this);
		PacketHandler.init();

		if (event.getSide() == Side.CLIENT) {
			mcThread = Thread.currentThread();
		}
	}

	@EventHandler
	public void serverLoad(FMLServerStartingEvent event) {
		event.registerServerCommand(new RedirectCommand());
		event.registerServerCommand(new FallbackCommand());
		event.registerServerCommand(new IfPlayerRedirectCommand(false, "ifplayercanredirect"));
		event.registerServerCommand(new IfPlayerRedirectCommand(true, "ifplayercannotredirect"));
	}

	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != Phase.END) {
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		if (redirectServerAddress != null) {
			String addr = redirectServerAddress;
			redirectServerAddress = null;
			fallbackServerAddress = null;
			redirect(addr);
		} else if (connected != (mc.theWorld != null)) {
			connected = mc.theWorld != null;
			if (connected) {
				PacketHandler.ANNOUNCE_CHANNEL.sendToServer(VoidMessage.INSTANCE);
			}
		} else if (fallbackServerAddress != null) {
			if (mc.currentScreen instanceof GuiDisconnected) {
				String addr = fallbackServerAddress;
				fallbackServerAddress = null;
				redirectServerAddress = null;
				redirect(addr);
			} else if (mc.currentScreen instanceof GuiMainMenu || mc.currentScreen instanceof GuiMultiplayer) {
				fallbackServerAddress = null;
				redirectServerAddress = null;
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		players.remove(event.player.getUniqueID());
	}

	/**
	 * Processes the redirect client side.<br>
	 * This simulates clicking the disconnect button and a direct connection to the specified server address.
	 * 
	 * @param serverAddress the new server address this client should connect to
	 * @throws IllegalStateException if called while not in the main thread
	 */
	@SideOnly(Side.CLIENT)
	public static void redirect(String serverAddress) {
		if (Thread.currentThread() != mcThread) {
			throw new IllegalStateException("Not in the main thread");
		}

		if (MinecraftForge.EVENT_BUS.post(new RedirectEvent(serverAddress))) {
			return;
		}

		LOGGER.info("Connecting to " + serverAddress);

		final Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld != null) {
			mc.theWorld.sendQuittingDisconnectingPacket();
			mc.loadWorld((WorldClient) null);
		}
		mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
		mc.displayGuiScreen(new GuiConnecting(mc.currentScreen, mc, new ServerData("ServerRedirect", serverAddress, false)));
	}

	@SideOnly(Side.CLIENT)
	public static String getFallbackServerAddress() {
		return fallbackServerAddress;
	}

	@SideOnly(Side.CLIENT)
	public static void setFallbackServerAddress(String fallbackServerAddress) {
		ServerRedirect.fallbackServerAddress = fallbackServerAddress;
	}

	@SideOnly(Side.CLIENT)
	public static String getRedirectServerAddress() {
		return redirectServerAddress;
	}

	@SideOnly(Side.CLIENT)
	public static void setRedirectServerAddress(String redirectServerAddress) {
		ServerRedirect.redirectServerAddress = redirectServerAddress;
	}

	/**
	 * Connects the specified player to the specified server address.<br>
	 * The client must have this mod in order for this to work.
	 * 
	 * @param serverAddress the new server address the player should connect to
	 * @param player the player's instance
	 * @return true if the redirect message was sent to the specified player
	 */
	public static boolean sendTo(EntityPlayerMP player, String serverAddress) {
		if (MinecraftForge.EVENT_BUS.post(new PlayerRedirectEvent(player, serverAddress))) {
			return false;
		}

		PacketHandler.REDIRECT_CHANNEL.sendTo(new AddressMessage(serverAddress), player);
		return true;
	}

	/**
	 * Connects all players with this mod on their client to the specified server address.
	 * 
	 * @param serverAddress the new server address the players should connect to
	 */
	public static void sendToAll(String serverAddress) {
		final AddressMessage message = new AddressMessage(serverAddress);

		@SuppressWarnings("unchecked")
		final List<EntityPlayerMP> list = (List<EntityPlayerMP>) MinecraftServer.getServer().getConfigurationManager().playerEntityList;
		for (EntityPlayerMP player : list) {
			if (!MinecraftForge.EVENT_BUS.post(new PlayerRedirectEvent(player, serverAddress))) {
				PacketHandler.REDIRECT_CHANNEL.sendTo(message, player);
			}
		}
	}

	/**
	 * Connects the specified player to the specified server address.<br>
	 * The client must have this mod in order for this to work.
	 * 
	 * @param serverAddress the new server address the player should connect to
	 * @param player the player's instance
	 * @return true if the redirect message was sent to the specified player
	 */
	public static boolean sendFallbackTo(EntityPlayerMP player, String serverAddress) {
		PacketHandler.FALLBACK_CHANNEL.sendTo(new AddressMessage(serverAddress), player);
		return true;
	}

	/**
	 * Connects all players with this mod on their client to the specified server address.
	 * 
	 * @param serverAddress the new server address the players should connect to
	 */
	public static void sendFallbackToAll(String serverAddress) {
		final AddressMessage message = new AddressMessage(serverAddress);

		@SuppressWarnings("unchecked")
		final List<EntityPlayerMP> list = (List<EntityPlayerMP>) MinecraftServer.getServer().getConfigurationManager().playerEntityList;
		for (EntityPlayerMP player : list) {
			PacketHandler.FALLBACK_CHANNEL.sendTo(message, player);
		}
	}

	/**
	 * 
	 * <b>WARNING:</b> this will likely return false for a player that just logged in,
	 * as it takes some time for the client to send the announce packet to the server. 
	 * 
	 * @param player the player to check
	 * @return whether the specified player is using Server Redirect
	 */
	public static boolean isUsingServerRedirect(EntityPlayer player) {
		return isUsingServerRedirect(player.getUniqueID());
	}

	/**
	 * 
	 * <b>WARNING:</b> this will likely return false for a player that just logged in,
	 * as it takes some time for the client to send the announce packet to the server. 
	 * 
	 * @param playerId the player to check
	 * @return whether the specified player is using Server Redirect
	 */
	public static boolean isUsingServerRedirect(UUID playerId) {
		return players.contains(playerId);
	}

	/**
	 * 
	 * Loop through the players with this mod<br>
	 * <br>
	 * <b>WARNING:</b> this will likely not include a player that just logged in,
	 * as it takes some time for the client to send the announce packet to the server. 
	 * 
	 * @param consumer a consumer that can do something with the player's UUID
	 */
	public static void forEachPlayerUsingServerRedirect(Consumer<UUID> consumer) {
		synchronized(players) {
			for (UUID playerId : players) {
				consumer.accept(playerId);
			}
		}
	}

	/**
	 * 
	 * An immutable copy of the set containing the players with this mod.<br>
	 * <br>
	 * For better performances, try to use the following methods instead:
	 * <ul>
	 * <li>{@link #isUsingServerRedirect(UUID)} to check whether a player is using this mod</li>
	 * <li>{@link #forEachPlayerUsingServerRedirect(Consumer)} to loop through the players with this mod</li>
	 * </ul>
	 * <b>WARNING:</b> this will likely not include a player that just logged in,
	 * as it takes some time for the client to send the announce packet to the server. 
	 * 
	 * @return an immutable copy of the players with this mod
	 */
	public static Set<UUID> getPlayers() {
		return Collections.unmodifiableSet(new HashSet<>(players));
	}

	/**
	 * Utility method for getting a player by UUID
	 * 
	 * @param playerId the player's UUID
	 * @return the EntityPlayerMP instance of the specified player, null if the player was not found.
	 */
	public static EntityPlayerMP getPlayer(UUID playerId) {
		final List<?> list = MinecraftServer.getServer().getConfigurationManager().playerEntityList;
		for (final Object playerObj : list) {
			if (((EntityPlayerMP) playerObj).getUniqueID().equals(playerId)) {
				return ((EntityPlayerMP) playerObj);
			}
		}

		return null;
	}

	/**
	 * Utility method for getting a player by username
	 * 
	 * @param playerName the player's username
	 * @return the EntityPlayerMP instance of the specified player, null if the player was not found.
	 */
	public static EntityPlayerMP getPlayer(String playerName) {
		final List<?> list = MinecraftServer.getServer().getConfigurationManager().playerEntityList;
		for (final Object playerObj : list) {
			if (((EntityPlayerMP) playerObj).getCommandSenderName().equalsIgnoreCase(playerName)) {
				return ((EntityPlayerMP) playerObj);
			}
		}

		return null;
	}
}
