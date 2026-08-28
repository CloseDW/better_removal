package com.better_removal.networking;

import com.better_removal.BetterRemoval;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BetterRemovalNetwork {

	private static final String PROTOCOL_VERSION = "1";

	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			ResourceLocation.fromNamespaceAndPath(BetterRemoval.MODID, "main"),
			() -> PROTOCOL_VERSION,
			PROTOCOL_VERSION::equals,
			PROTOCOL_VERSION::equals);

	private static int id = 0;

	private BetterRemovalNetwork() {
	}

	public static void register() {
		CHANNEL.messageBuilder(ExtractionModeCyclePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
				.encoder(ExtractionModeCyclePacket::encode)
				.decoder(ExtractionModeCyclePacket::new)
				.consumerMainThread(ExtractionModeCyclePacket::handle)
				.add();

		CHANNEL.messageBuilder(AltKeyStatePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
				.encoder(AltKeyStatePacket::encode)
				.decoder(AltKeyStatePacket::new)
				.consumerMainThread(AltKeyStatePacket::handle)
				.add();

		CHANNEL.messageBuilder(ExtractionModeSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
				.encoder(ExtractionModeSyncPacket::encode)
				.decoder(ExtractionModeSyncPacket::new)
				.consumerMainThread(ExtractionModeSyncPacket::handle)
				.add();
	}
}