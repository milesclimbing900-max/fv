package com.deepscan;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class DeepScan implements ClientModInitializer {
	public static final String ID = "deepscan";

	@Override
	public void onInitializeClient() {
		Config.load();

		KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
		KeyMapping scanKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.deepscan.scan", InputConstants.Type.KEYSYM, InputConstants.KEY_R, cat));
		KeyMapping menuKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.deepscan.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, cat));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (scanKey.consumeClick()) Scanner.start(mc);
			while (menuKey.consumeClick()) mc.gui.setScreen(new MenuScreen());
			Scanner.tick(mc);
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(ID, "overlay"), Overlay::render);
	}
}
