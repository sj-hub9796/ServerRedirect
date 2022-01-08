package net.kaikk.mc.serverredirect.forge;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
	private static final String PROTOCOL_VERSION = "1";
	public static final SimpleChannel REDIRECT_CHANNEL = NetworkRegistry.newSimpleChannel(
			new ResourceLocation("srvredirect", "red"),
			() -> PROTOCOL_VERSION,
			NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
			NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)
			);
	public static final SimpleChannel FALLBACK_CHANNEL = NetworkRegistry.newSimpleChannel(
			new ResourceLocation("srvredirect", "fal"),
			() -> PROTOCOL_VERSION,
			NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
			NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)
			);
	public static final Pattern ADDRESS_PREVALIDATOR = Pattern.compile("^[A-Za-z0-9-_.:]+$"); // allowed characters in a server address

	public static void init() {
		REDIRECT_CHANNEL.registerMessage(0, String.class, PacketHandler::encode, PacketHandler::decode, PacketHandler::handleRedirect, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
		FALLBACK_CHANNEL.registerMessage(0, String.class, PacketHandler::encode, PacketHandler::decode, PacketHandler::handleFallback, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
	}

	public static void encode(String addr, FriendlyByteBuf buffer) {
		buffer.writeCharSequence(addr, StandardCharsets.UTF_8);
	}

	public static String decode(FriendlyByteBuf buffer) {
		return buffer.toString(StandardCharsets.UTF_8);
	}

	public static void handleRedirect(String addr, Supplier<Context> ctx) {
		if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_CLIENT && ADDRESS_PREVALIDATOR.matcher(addr).matches()) {
			ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ServerRedirect.redirect(addr)));
		}
		ctx.get().setPacketHandled(true);
	}

	public static void handleFallback(String addr, Supplier<Context> ctx) {
		if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_CLIENT && ADDRESS_PREVALIDATOR.matcher(addr).matches()) {
			ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ServerRedirect.setFallbackServerAddress(addr)));
		}
		ctx.get().setPacketHandled(true);
	}
}