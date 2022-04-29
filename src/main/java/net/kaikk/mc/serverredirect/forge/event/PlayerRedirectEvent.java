package net.kaikk.mc.serverredirect.forge.event;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * Called when the server is sending a transfer packet to the player
 *
 */
@Cancelable
public class PlayerRedirectEvent extends Event {
	protected final ServerPlayerEntity player;
	protected final String address;
	
	public PlayerRedirectEvent(ServerPlayerEntity player, String address) {
		this.player = player;
		this.address = address;
	}

	public ServerPlayerEntity getPlayer() {
		return player;
	}

	public String getAddress() {
		return address;
	}
}
