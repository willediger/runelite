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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.gameval.SpotanimID;

@Slf4j
@Singleton
public class ManticoreHandler
{
	@Inject
	private Client client;

	@Inject
	private ColosseumWavesLogger cwLog;

	// Callback for when a manticore pattern is completed
	private Runnable onPatternCompleteCallback;

	private boolean mantimayhem3Active = false;

	private static final int MAGIC_ORB_GRAPHIC_ID = SpotanimID.VFX_MANTICORE_01_PROJECTILE_MAGIC_01;
	private static final int RANGED_ORB_GRAPHIC_ID = SpotanimID.VFX_MANTICORE_01_PROJECTILE_RANGED_01;
	private static final int MELEE_ORB_GRAPHIC_ID = SpotanimID.VFX_MANTICORE_01_PROJECTILE_MELEE_01;

	private final Map<Integer, ManticoreData> manticores = new HashMap<>();

	private enum OrbType
	{
		MAGIC('m'), RANGED('r'), MELEE('M');
		final char code;

		OrbType(char code)
		{
			this.code = code;
		}
	}

	private static class ManticoreData
	{
		List<OrbType> orbOrder = new ArrayList<>();  // Orb order, max 3 entries
		boolean wasChargedAtReinforcements = false;  // Whether it had any orbs when reinforcements spawned
		int lastCheckedGraphic = -1;  // Track the last graphic we checked

		boolean isCharged()
		{
			return orbOrder.size() >= 3;
		}

		String getLosSuffix(boolean isMantimayhem3Active)
		{
			if (orbOrder.isEmpty())
			{
				return "u"; // Uncharged
			}

			// With MM3 active and full sequence
			if (isMantimayhem3Active && orbOrder.size() == 3)
			{
				// If third orb is melee, it's a standard sequence - abbreviate
				if (orbOrder.get(2) == OrbType.MELEE)
				{
					return String.valueOf(orbOrder.get(0).code);
				}
				// Non-standard sequence - return full sequence
				StringBuilder sb = new StringBuilder();
				for (OrbType orb : orbOrder)
				{
					sb.append(orb.code);
				}
				return sb.toString();
			}

			// Without MM3 or incomplete sequence, return first orb
			return String.valueOf(orbOrder.get(0).code);
		}
	}

	public String getManticoreLosSuffix(int npcIndex)
	{
		ManticoreData data = manticores.get(npcIndex);
		if (data == null)
		{
			log.debug("Manticore {} not found in tracking map", npcIndex);
			return "u";
		}
		String suffix = data.getLosSuffix(isMantimayhem3Active());
		return suffix;
	}

	public String getManticoreSpawnLosSuffix(int npcIndex, boolean isReinforcement)
	{
		ManticoreData data = manticores.get(npcIndex);
		if (data == null)
		{
			cwLog.logError(String.format("Manticore %d not found for %s URL",
				npcIndex, isReinforcement ? "reinforcement" : "spawn"));
			return "u";
		}

		boolean isMM3Active = isMantimayhem3Active();

		// For reinforcements, check if it was charged at that time
		if (isReinforcement)
		{
			if (!data.wasChargedAtReinforcements)
			{
				// Was uncharged at reinforcements, but might be charged now or have partial pattern
				if (data.isCharged() || !data.orbOrder.isEmpty())
				{
					String suffix = data.getLosSuffix(isMM3Active);
					return "u" + suffix; // e.g., "ur", "um", "urmM"
				}
				return "u";
			}
			// Was charged at reinforcements
			return data.getLosSuffix(isMM3Active);
		}
		else
		{
			// For initial spawn, manticores are ALWAYS uncharged at spawn
			// But we might know the pattern now
			if (data.isCharged() || !data.orbOrder.isEmpty())
			{
				String suffix = data.getLosSuffix(isMM3Active);
				return "u" + suffix; // Always prefix with "u" for spawn
			}
			return "u";
		}
	}

	public boolean isManticoreUncharged(NPC npc)
	{
		return isManticoreUncharged(npc.getIndex());
	}

	public boolean isManticoreUncharged(int npcIndex)
	{
		ManticoreData data = manticores.get(npcIndex);
		return data == null || !data.isCharged();
	}

	public boolean hasCompletePattern(int npcIndex)
	{
		ManticoreData data = manticores.get(npcIndex);
		if (data == null)
		{
			return false;
		}
		// Without MM3, having first orb is enough
		// With MM3, need full sequence (3 orbs)
		return !isMantimayhem3Active() ? !data.orbOrder.isEmpty() : data.isCharged();
	}

	public int getOrbCount(int npcIndex)
	{
		ManticoreData data = manticores.get(npcIndex);
		return data == null ? 0 : data.orbOrder.size();
	}

	public void setMantimayhem3Active(boolean active)
	{
		this.mantimayhem3Active = active;
	}

	public boolean isMantimayhem3Active()
	{
		return mantimayhem3Active;
	}

	public void clear()
	{
		manticores.clear();
	}

	public void captureSpawnStates(boolean isReinforcement)
	{
		if (isReinforcement)
		{
			// Mark which manticores had any orbs at reinforcement time
			for (Map.Entry<Integer, ManticoreData> entry : manticores.entrySet())
			{
				ManticoreData data = entry.getValue();
				// Track if it had ANY orbs at reinforcements, not just fully charged
				data.wasChargedAtReinforcements = !data.orbOrder.isEmpty();
				cwLog.logManticoreStateAtReinforcements(entry.getKey(),
					data.wasChargedAtReinforcements,
					data.orbOrder.stream().map(o -> o.name()).collect(java.util.stream.Collectors.toList()));
			}
		}
	}

	public void onNpcSpawned(NPC npc)
	{
		int index = npc.getIndex();
		ManticoreData data = new ManticoreData();
		data.lastCheckedGraphic = npc.getGraphic(); // Store initial graphic state
		manticores.put(index, data);
		cwLog.logDebug("Manticore " + index + " spawned with initial graphic: " + data.lastCheckedGraphic);
	}

	public void ensureManticoreTracked(NPC npc)
	{
		int index = npc.getIndex();
		// Only add if not already tracked
		if (!manticores.containsKey(index))
		{
			ManticoreData data = new ManticoreData();
			data.lastCheckedGraphic = npc.getGraphic();
			manticores.put(index, data);
			cwLog.logDebug("Added manticore " + index + " to tracking (was not caught by spawn event), initial graphic: " + data.lastCheckedGraphic);
		}
	}

	public void checkNPCGraphics(NPC npc)
	{
		int graphic = npc.getGraphic();
		int index = npc.getIndex();

		if (graphic == MAGIC_ORB_GRAPHIC_ID || graphic == RANGED_ORB_GRAPHIC_ID || graphic == MELEE_ORB_GRAPHIC_ID)
		{
			ManticoreData data = manticores.get(index);
			if (data == null)
			{
				cwLog.logError(String.format("Manticore %d: Orb graphic detected but manticore not in tracking map!", index));
				return;
			}

			// Only track if we haven't reached 3 orbs yet
			if (data.orbOrder.size() >= 3)
			{
				cwLog.logManticoreAlreadyCharged(index);
				return;
			}

			OrbType orbType = null;
			if (graphic == MAGIC_ORB_GRAPHIC_ID)
			{
				orbType = OrbType.MAGIC;
			}
			else if (graphic == RANGED_ORB_GRAPHIC_ID)
			{
				orbType = OrbType.RANGED;
			}
			else if (graphic == MELEE_ORB_GRAPHIC_ID)
			{
				orbType = OrbType.MELEE;
			}

			if (orbType != null)
			{
				// Check if this is a new orb type in the sequence
				if (data.orbOrder.isEmpty() || data.orbOrder.get(data.orbOrder.size() - 1) != orbType)
				{
					boolean wasIncomplete = !hasCompletePattern(index);
					data.orbOrder.add(orbType);
					cwLog.logManticoreOrbDetected(index, orbType.name(),
						data.orbOrder.stream().map(o -> o.name()).collect(java.util.stream.Collectors.toList()));

					if (data.orbOrder.size() == 3)
					{
						cwLog.logManticoreFullyCharged(index,
							data.orbOrder.stream().map(o -> o.name()).collect(java.util.stream.Collectors.toList()));
					}

					// Check if pattern just became complete
					if (wasIncomplete && hasCompletePattern(index))
					{
						cwLog.logDebug(String.format("Manticore %d pattern complete, triggering URL update", index));
						if (onPatternCompleteCallback != null)
						{
							onPatternCompleteCallback.run();
						}
					}
				}
			}

			// Store the last checked graphic
			data.lastCheckedGraphic = graphic;
		}
	}

	public void checkAllManticores()
	{
		for (NPC npc : client.getNpcs())
		{
			if (npc.getId() != net.runelite.api.gameval.NpcID.COLOSSEUM_MANTICORE)
			{
				continue;
			}

			int index = npc.getIndex();
			ManticoreData data = manticores.get(index);

			if (data != null)
			{
				int currentGraphic = npc.getGraphic();

				// Check if the graphic changed from what we last saw
				if (currentGraphic != data.lastCheckedGraphic)
				{
					// Process the new graphic
					checkNPCGraphics(npc);
				}
			}
		}
	}

	public void setOnPatternCompleteCallback(Runnable callback)
	{
		this.onPatternCompleteCallback = callback;
	}
}