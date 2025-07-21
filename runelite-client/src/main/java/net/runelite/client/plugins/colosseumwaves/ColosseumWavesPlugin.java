/*
 * Copyright (c) 2025, Will Ediger <will.ediger@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.colosseumwaves;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.NPC;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import org.apache.commons.lang3.ArrayUtils;

@PluginDescriptor(
	name = "Colosseum Waves",
	description = "Capture Fortis Colosseum wave spawns and pillar stacks and generate shareable links to line of sight tool for analysis",
	tags = {"fortis", "colosseum", "waves", "wave", "spawns", "los"}
)
@Slf4j
public class ColosseumWavesPlugin extends Plugin
{
	private static final boolean DEV_MODE = false;

	private void seedTestData()
	{
		// Add test waves for scroll testing
		for (int w = -20; w <= 0; w++)
		{
			panel.addWave(w);
		}
	}

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	private ColosseumWavesPanel panel;
	private NavigationButton navButton;

	private ManticoreHandler manticoreHandler;

	// Fortis Colosseum region ID
	private static final int COLOSSEUM_REGION_ID = 7216;

	// NPCs to exclude from tracking
	private static final Set<String> EXCLUDED_NPC_NAMES = new HashSet<>();

	static
	{
		EXCLUDED_NPC_NAMES.add("Minimus");
		EXCLUDED_NPC_NAMES.add("Sol Heredit");
		EXCLUDED_NPC_NAMES.add("Fremennik warband berserker");
		EXCLUDED_NPC_NAMES.add("Fremennik warband archer");
		EXCLUDED_NPC_NAMES.add("Fremennik warband seer");
	}

	// Colosseum wave NPCs with their LoS tool ID mapping
	private static final Map<Integer, Integer> COLOSSEUM_WAVE_NPCS = new HashMap<>();

	static
	{
		COLOSSEUM_WAVE_NPCS.put(12811, 1); // Serpent shaman
		COLOSSEUM_WAVE_NPCS.put(12817, 2); // Javelin Colossus
		COLOSSEUM_WAVE_NPCS.put(12810, 3); // Jaguar warrior
		COLOSSEUM_WAVE_NPCS.put(12818, 4); // Manticore
		COLOSSEUM_WAVE_NPCS.put(12812, 5); // Minotaur
		COLOSSEUM_WAVE_NPCS.put(12813, 5); // Minotaur (Red Flag)
		COLOSSEUM_WAVE_NPCS.put(12819, 6); // Shockwave Colossus
	}

	// Pattern to match "Wave: X" messages
	private static final Pattern WAVE_PATTERN = Pattern.compile("Wave: (\\d+)");

	private Point currentPlayerLoSLocation = null;
	private Map<NPC, Point> npcLastPositions = new HashMap<>();
	private List<NPCSpawn> waveSpawns = new ArrayList<>();
	private long lastWaveSpawnTime = 0;
	private long waveStartTime = 0;
	private boolean waveComplete = false;
	private boolean inColosseum = false;
	private int currentWave = 0;
	private Map<NPC, NPCSpawn> activeNPCs = new HashMap<>();
	private boolean isReinforcementWave = false;
	private boolean expectingWaveSpawn = false;

	// Helper class to store NPC spawn info
	private static class NPCSpawn
	{
		final int npcId;
		final Point location;
		final String name;

		NPCSpawn(int npcId, Point location, String name)
		{
			this.npcId = npcId;
			this.location = location;
			this.name = name;
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		currentPlayerLoSLocation = null;
		npcLastPositions.clear();
		waveSpawns.clear();
		activeNPCs.clear();
		inColosseum = false;
		currentWave = 0;
		isReinforcementWave = false;
		expectingWaveSpawn = false;
		waveStartTime = 0;

		panel = injector.getInstance(ColosseumWavesPanel.class);

		if (DEV_MODE)
		{
			log.info("Colosseum Waves Plugin running in DEVELOPMENT MODE");
			seedTestData();
		}

		manticoreHandler = new ManticoreHandler(client, this);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "colosseum_icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Colosseum Waves")
			.icon(icon)
			.priority(10)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (manticoreHandler != null)
		{
			manticoreHandler.clear();
		}

		// Clean up
		currentPlayerLoSLocation = null;
		npcLastPositions.clear();
		waveSpawns.clear();
		activeNPCs.clear();
		inColosseum = false;
		currentWave = 0;
		isReinforcementWave = false;
		expectingWaveSpawn = false;
		waveStartTime = 0;

		// Remove panel
		clientToolbar.removeNavigation(navButton);
		panel = null;

	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = event.getMessage();
		Matcher matcher = WAVE_PATTERN.matcher(message);

		if (matcher.find())
		{
			int newWave = Integer.parseInt(matcher.group(1));

			if (!DEV_MODE)
			{
				// Reset panel ONLY when starting Wave 1 (new run)
				if (newWave == 1 && panel != null)
				{
					panel.reset();
				}
			}

			currentWave = newWave;
			waveStartTime = System.currentTimeMillis();
			expectingWaveSpawn = true;
			waveSpawns.clear();
			activeNPCs.clear();
			waveComplete = false;
			isReinforcementWave = false;

			// Add wave to panel
			if (panel != null)
			{
				panel.addWave(currentWave);
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (inColosseum && event.getGameState() == GameState.LOGGED_IN)
		{
			if (!isInColosseum())
			{
				inColosseum = false;
				npcLastPositions.clear();
				waveSpawns.clear();
				activeNPCs.clear();
				currentWave = 0;
				isReinforcementWave = false;
				expectingWaveSpawn = false;
				waveStartTime = 0;
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		// Check if we entered/left the Colosseum
		if (!inColosseum && isInColosseum())
		{
			inColosseum = true;
		}
		else if (inColosseum && !isInColosseum())
		{
			inColosseum = false;
			npcLastPositions.clear();
			waveSpawns.clear();
			activeNPCs.clear();
			currentWave = 0;
			isReinforcementWave = false;
			expectingWaveSpawn = false;
			waveStartTime = 0;
		}

		// Track player position for LoS coordinates
		if (inColosseum)
		{
			WorldPoint worldLocation = localPlayer.getWorldLocation();
			WorldView wv = client.getTopLevelWorldView();
			LocalPoint localPoint = LocalPoint.fromWorld(wv, worldLocation);

			if (localPoint != null)
			{
				Scene scene = client.getScene();
				Tile[][][] tiles = scene.getTiles();

				int sceneX = localPoint.getSceneX();
				int sceneY = localPoint.getSceneY();
				int plane = client.getPlane();

				if (sceneX >= 0 && sceneX < 104 && sceneY >= 0 && sceneY < 104 && plane >= 0 && plane < 4)
				{
					Tile playerTile = tiles[plane][sceneX][sceneY];
					if (playerTile != null)
					{
						Point sceneLocation = playerTile.getSceneLocation();
						currentPlayerLoSLocation = convertToLoSCoordinates(sceneLocation);
					}
				}
			}

			// Track NPC movements and check manticore graphics
			trackNPCMovements();
			checkManticoreGraphics();

			// Generate LoS link after wave spawning is complete
			if (waveComplete && !waveSpawns.isEmpty() &&
				System.currentTimeMillis() - lastWaveSpawnTime > 500)
			{
				generateLoSToolLink();
				waveComplete = false;
			}
		}
	}

	private boolean isInColosseum()
	{
		return ArrayUtils.contains(client.getMapRegions(), COLOSSEUM_REGION_ID);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (!inColosseum)
		{
			return;
		}

		NPC npc = event.getNpc();

		// Track manticores
		if (manticoreHandler != null)
		{
			manticoreHandler.onNpcSpawned(npc);
		}

		// Skip excluded NPCs
		if (npc.getName() != null && EXCLUDED_NPC_NAMES.contains(npc.getName()))
		{
			return;
		}

		Point npcLocation = getNPCSceneLocation(npc);

		if (npcLocation != null)
		{
			npcLastPositions.put(npc, npcLocation);

			// Store spawn position for LoS tool generation
			if (COLOSSEUM_WAVE_NPCS.containsKey(npc.getId()))
			{
				long currentTime = System.currentTimeMillis();

				// Determine if this is a wave spawn or reinforcement
				if (expectingWaveSpawn && waveStartTime > 0 && currentTime - waveStartTime < 10000)
				{
					expectingWaveSpawn = false;
					isReinforcementWave = false;
				}
				else if (waveStartTime > 0 && currentTime - waveStartTime > 30000)
				{
					if (!isReinforcementWave)
					{
						isReinforcementWave = true;
					}
				}

				lastWaveSpawnTime = currentTime;

				NPCSpawn spawn = new NPCSpawn(npc.getId(), npcLocation, npc.getName());
				activeNPCs.put(npc, spawn);

				if (!isReinforcementWave)
				{
					waveSpawns.add(spawn);
				}

				if (isReinforcementWave)
				{
					captureAllNPCPositions();
				}

				if (!waveComplete)
				{
					waveComplete = true;
				}
			}
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (!inColosseum)
		{
			return;
		}

		NPC npc = event.getNpc();

		// Track manticore despawn
		if (manticoreHandler != null)
		{
			manticoreHandler.onNpcDespawned(npc);
		}

		npcLastPositions.remove(npc);
		activeNPCs.remove(npc);
	}

	private void trackNPCMovements()
	{
		Map<NPC, Point> currentPositions = new HashMap<>(npcLastPositions);

		for (Map.Entry<NPC, Point> entry : currentPositions.entrySet())
		{
			NPC npc = entry.getKey();
			if (npc == null || npc.getName() == null)
			{
				npcLastPositions.remove(npc);
				activeNPCs.remove(npc);
				continue;
			}

			if (EXCLUDED_NPC_NAMES.contains(npc.getName()))
			{
				npcLastPositions.remove(npc);
				activeNPCs.remove(npc);
				continue;
			}

			Point lastPos = entry.getValue();
			Point currentPos = getNPCSceneLocation(npc);

			if (currentPos == null)
			{
				npcLastPositions.remove(npc);
				activeNPCs.remove(npc);
			}
			else if (!currentPos.equals(lastPos))
			{
				npcLastPositions.put(npc, currentPos);

				if (activeNPCs.containsKey(npc))
				{
					NPCSpawn oldSpawn = activeNPCs.get(npc);
					activeNPCs.put(npc, new NPCSpawn(oldSpawn.npcId, currentPos, oldSpawn.name));
				}
			}
		}
	}

	private void checkManticoreGraphics()
	{
		if (!inColosseum || manticoreHandler == null)
		{
			return;
		}

		for (NPC npc : client.getNpcs())
		{
			if (npc != null && npc.getId() == 12818) // Manticore
			{
				manticoreHandler.checkNPCGraphics(npc);
			}
		}
	}

	private void captureAllNPCPositions()
	{
		waveSpawns.clear();

		for (Map.Entry<NPC, NPCSpawn> entry : activeNPCs.entrySet())
		{
			NPC npc = entry.getKey();
			if (npc != null && COLOSSEUM_WAVE_NPCS.containsKey(npc.getId()))
			{
				Point currentPos = getNPCSceneLocation(npc);
				if (currentPos != null)
				{
					waveSpawns.add(new NPCSpawn(npc.getId(), currentPos, npc.getName()));
				}
			}
		}
	}

	private Point getNPCSceneLocation(NPC npc)
	{
		if (npc == null)
		{
			return null;
		}

		LocalPoint localPoint = npc.getLocalLocation();
		if (localPoint == null)
		{
			return null;
		}

		WorldPoint worldPoint = npc.getWorldLocation();
		WorldView wv = client.getTopLevelWorldView();
		LocalPoint worldLocalPoint = LocalPoint.fromWorld(wv, worldPoint);

		if (worldLocalPoint == null)
		{
			return null;
		}

		int sceneX = worldLocalPoint.getSceneX();
		int sceneY = worldLocalPoint.getSceneY();

		// Check if the NPC is within the colosseum bounds
		if (sceneX < 35 || sceneX > 62 || sceneY < 53 || sceneY > 82)
		{
			return null;
		}

		return new Point(sceneX, sceneY);
	}

	private Point convertToLoSCoordinates(Point sceneLocation)
	{
		int losX = sceneLocation.getX() - 32;
		int losY = 83 - sceneLocation.getY();

		losX = Math.max(0, Math.min(33, losX));
		losY = Math.max(0, Math.min(33, losY));

		return new Point(losX, losY);
	}

	private void generateLoSToolLink()
	{
		if (waveSpawns.isEmpty())
		{
			return;
		}

		StringBuilder urlBuilder = new StringBuilder("https://los.colosim.com/?");

		for (NPCSpawn spawn : waveSpawns)
		{
			Point losPos = convertToLoSCoordinates(spawn.location);
			Integer losNpcId = COLOSSEUM_WAVE_NPCS.get(spawn.npcId);

			if (losNpcId != null)
			{
				String spawnCode = String.format("%02d%02d%d", losPos.getX(), losPos.getY(), losNpcId);
				urlBuilder.append(spawnCode);

				// Add suffix for manticores based on detected pattern
				if (spawn.npcId == 12818)
				{
					char suffix = 'm'; // Default to mage-first

					for (Map.Entry<NPC, NPCSpawn> entry : activeNPCs.entrySet())
					{
						NPCSpawn activeSpawn = entry.getValue();
						if (activeSpawn.npcId == spawn.npcId &&
							activeSpawn.location.equals(spawn.location) &&
							manticoreHandler != null)
						{
							suffix = manticoreHandler.getManticoreLosSuffix(entry.getKey());
							break;
						}
					}

					urlBuilder.append(suffix);
				}

				urlBuilder.append(".");
			}
		}

		String url = urlBuilder.toString();

		// Update panel with the URL
		if (panel != null && currentWave > 0)
		{
			if (isReinforcementWave)
			{
				panel.setWaveReinforcementUrl(currentWave, url);
			}
			else
			{
				panel.setWaveSpawnUrl(currentWave, url);
			}
		}

		// Reset reinforcement flag
		if (isReinforcementWave)
		{
			isReinforcementWave = false;
		}
	}

	/**
	 * Generate a LoS link for the current NPC positions
	 */
	public String generateCurrentLoSLink()
	{
		if (!inColosseum)
		{
			return null;
		}

		waveSpawns.clear();

		for (Map.Entry<NPC, NPCSpawn> entry : activeNPCs.entrySet())
		{
			NPCSpawn spawn = entry.getValue();
			if (spawn != null && COLOSSEUM_WAVE_NPCS.containsKey(spawn.npcId))
			{
				waveSpawns.add(spawn);
			}
		}

		if (waveSpawns.isEmpty())
		{
			return null;
		}

		StringBuilder urlBuilder = new StringBuilder("https://los.colosim.com/?");

		for (NPCSpawn spawn : waveSpawns)
		{
			Point losPos = convertToLoSCoordinates(spawn.location);
			Integer losNpcId = COLOSSEUM_WAVE_NPCS.get(spawn.npcId);

			if (losNpcId != null)
			{
				String spawnCode = String.format("%02d%02d%d", losPos.getX(), losPos.getY(), losNpcId);
				urlBuilder.append(spawnCode);

				// Add suffix for manticores
				if (spawn.npcId == 12818)
				{
					char suffix = 'm';

					for (Map.Entry<NPC, NPCSpawn> entry : activeNPCs.entrySet())
					{
						NPCSpawn activeSpawn = entry.getValue();
						if (activeSpawn.npcId == spawn.npcId &&
							activeSpawn.location.equals(spawn.location) &&
							manticoreHandler != null)
						{
							suffix = manticoreHandler.getManticoreLosSuffix(entry.getKey());
							break;
						}
					}

					urlBuilder.append(suffix);
				}

				urlBuilder.append(".");
			}
		}

		// Add player position
		if (currentPlayerLoSLocation != null)
		{
			int playerEncoded = currentPlayerLoSLocation.getX() + (256 * currentPlayerLoSLocation.getY());
			urlBuilder.append("#");
			urlBuilder.append(playerEncoded);
		}

		return urlBuilder.toString();
	}
}