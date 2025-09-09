package com.haexporter;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;

import com.google.inject.Provides;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.eventbus.Subscribe;
import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginDescriptor(
		name = "Home Assistant Exporter",
		description = "Exports OSRS data to Home Assistant via webhook"
)
public class HAExporter extends Plugin {

	@Inject
	private Client client;

	@Inject
	private HAExporterConfig config;

	@Inject
	private ClientThread clientThread;

	private Gson gson = new Gson();

	private Map<String, Integer> currentInventory = new HashMap<>();
	private Map<String, Integer> currentEquipment = new HashMap<>();
	private Map<String, Object> skillsMap = new HashMap<>();
	private String previousJson = "";

	@Provides
	HAExporterConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HAExporterConfig.class);
	}


	@Subscribe
	public void onGameTick(GameTick tick) {
		try {
			writeGameData();
		} catch (IOException e) {
			log.error("Error writing game data to HA: {}", e.getMessage());
		}
	}

	private void writeGameData() throws IOException {
		skillsMap.clear();

		for (Skill skill : Skill.values()) {
			Map<String, Object> skillData = new HashMap<>();
			skillData.put("level", client.getRealSkillLevel(skill));
			skillData.put("xp", client.getSkillExperience(skill));
			skillsMap.put(skill.getName().toLowerCase(), skillData);
		}

		Player player = client.getLocalPlayer();
		Actor npc = player.getInteracting();
		String npcName = npc != null ? npc.getName() : "null";

		WorldPoint playerLocation = player.getWorldLocation();

		// Collect inventory
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv != null) {
			currentInventory.clear();
			for (Item item : inv.getItems()) {
				if (item.getId() != -1) {
					currentInventory.put(String.valueOf(item.getId()), item.getQuantity());
				}
			}
		}

		ItemContainer eq = client.getItemContainer(InventoryID.EQUIPMENT);
		if (eq != null) {
			currentEquipment.clear();
			for (Item item : eq.getItems()) {
				if (item.getId() != -1) {
					currentEquipment.put(String.valueOf(item.getId()), item.getQuantity());
				}
			}
		}

		// Build JSON
		Map<String, Object> jsonData = new HashMap<>();
		jsonData.put("skills", new HashMap<>(skillsMap));
		jsonData.put("inventory", new HashMap<>(currentInventory));
		jsonData.put("equipment", new HashMap<>(currentEquipment));

		// Spellbook
		int spellbookVar = client.getVar(Varbits.SPELLBOOK);
		String spellbookName;
		if (spellbookVar == 0) {
			spellbookName = "standard";
		} else if (spellbookVar == 1) {
			spellbookName = "ancient";
		} else if (spellbookVar == 2) {
			spellbookName = "lunar";
		} else if (spellbookVar == 3) {
			spellbookName = "arceuus";
		} else {
			spellbookName = "unknown";
		}
		jsonData.put("spellbook", spellbookName);

		// Active prayers
		List<String> activePrayers = new ArrayList<>();
		int[] prayerVarbits = {
			Varbits.PRAYER_THICK_SKIN,
			Varbits.PRAYER_BURST_OF_STRENGTH,
			Varbits.PRAYER_CLARITY_OF_THOUGHT,
			Varbits.PRAYER_SHARP_EYE,
			Varbits.PRAYER_MYSTIC_WILL,
			Varbits.PRAYER_ROCK_SKIN,
			Varbits.PRAYER_SUPERHUMAN_STRENGTH,
			Varbits.PRAYER_IMPROVED_REFLEXES,
			Varbits.PRAYER_RAPID_RESTORE,
			Varbits.PRAYER_RAPID_HEAL,
			Varbits.PRAYER_PROTECT_ITEM,
			Varbits.PRAYER_HAWK_EYE,
			Varbits.PRAYER_MYSTIC_LORE,
			Varbits.PRAYER_STEEL_SKIN,
			Varbits.PRAYER_ULTIMATE_STRENGTH,
			Varbits.PRAYER_INCREDIBLE_REFLEXES,
			Varbits.PRAYER_PROTECT_FROM_MAGIC,
			Varbits.PRAYER_PROTECT_FROM_MISSILES,
			Varbits.PRAYER_PROTECT_FROM_MELEE,
			Varbits.PRAYER_EAGLE_EYE,
			Varbits.PRAYER_MYSTIC_MIGHT,
			Varbits.PRAYER_RETRIBUTION,
			Varbits.PRAYER_REDEMPTION,
			Varbits.PRAYER_SMITE,
			Varbits.PRAYER_PRESERVE,
			Varbits.PRAYER_CHIVALRY,
			Varbits.PRAYER_PIETY,
			Varbits.PRAYER_RIGOUR,
			Varbits.PRAYER_AUGURY
		};
		String[] prayerNames = {
			"thick_skin",
			"burst_of_strength",
			"clarity_of_thought",
			"sharp_eye",
			"mystic_will",
			"rock_skin",
			"superhuman_strength",
			"improved_reflexes",
			"rapid_restore",
			"rapid_heal",
			"protect_item",
			"hawk_eye",
			"mystic_lore",
			"steel_skin",
			"ultimate_strength",
			"incredible_reflexes",
			"protect_from_magic",
			"protect_from_missiles",
			"protect_from_melee",
			"eagle_eye",
			"mystic_might",
			"retribution",
			"redemption",
			"smite",
			"preserve",
			"chivalry",
			"piety",
			"rigour",
			"augury"
		};
		for (int i = 0; i < prayerVarbits.length; i++) {
			if (client.getVar(prayerVarbits[i]) == 1) {
				activePrayers.add(prayerNames[i]);
			}
		}
		jsonData.put("active_prayers", activePrayers);
		jsonData.put("health", client.getBoostedSkillLevel(Skill.HITPOINTS));
		jsonData.put("prayer_points", client.getBoostedSkillLevel(Skill.PRAYER));
		jsonData.put("special_energy", client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10);
		jsonData.put("run_energy", client.getEnergy() / 100);
		jsonData.put("weight", client.getWeight());
		jsonData.put("interacting_code", player.getInteracting() != null ? player.getInteracting().toString() : "null");
		jsonData.put("target_name", npcName);

		Map<String, Object> location = new HashMap<>();
		location.put("x", playerLocation.getX());
		location.put("y", playerLocation.getY());
		location.put("plane", playerLocation.getPlane());
		location.put("region_id", playerLocation.getRegionID());
		location.put("region_x", playerLocation.getRegionX());
		location.put("region_y", playerLocation.getRegionY());
		jsonData.put("world_location", location);

		String json = gson.toJson(jsonData);
		if (!json.equals(previousJson)) {
			sendToWebhook(json);
			previousJson = json;
		}
	}




	private void sendToWebhook(String json) {
		String url = config.WebhookUrl();
		if (url.isEmpty()) return;
		try {
			HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setDoOutput(true);
			try (OutputStream os = conn.getOutputStream()) {
				os.write(json.getBytes());
			}
			int responseCode = conn.getResponseCode();
			log.info("Webhook sent, response: {}", responseCode);
		} catch (IOException e) {
			log.error("Failed to send webhook", e);
		}
	}
}