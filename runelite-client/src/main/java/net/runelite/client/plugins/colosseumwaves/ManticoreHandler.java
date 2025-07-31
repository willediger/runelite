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

	private boolean mantimayhem3Active = false;

	private static final int MAGIC_ORB_GRAPHIC_ID = SpotanimID.VFX_MANTICORE_01_PROJECTILE_MAGIC_01;
	private static final int RANGED_ORB_GRAPHIC_ID = SpotanimID.VFX_MANTICORE_01_PROJECTILE_RANGED_01;
	private static final int MELEE_ORB_GRAPHIC_ID = SpotanimID.VFX_MANTICORE_01_PROJECTILE_MELEE_01;

	private final Map<Integer, ManticoreData> manticores = new HashMap<>();
	private final Map<Integer, ManticoreData> initialSpawnStates = new HashMap<>();
	private final Map<Integer, ManticoreData> reinforcementSpawnStates = new HashMap<>();

	private enum OrbType
	{
		MAGIC('m'), RANGED('r'), MELEE('l');
		final char code;

		OrbType(char code)
		{
			this.code = code;
		}
	}

	private static class ManticoreData
	{
		boolean charged = false;
		List<OrbType> orbSequence = new ArrayList<>();
		boolean sequenceComplete = false;

		String getLosSuffix(boolean isMantimayhem3Active)
		{
			if (!charged || orbSequence.isEmpty())
			{
				return "u"; // Uncharged
			}

			// Check if we have a complete sequence
			if (sequenceComplete && orbSequence.size() == 3)
			{
				// If third orb is melee, it's a standard sequence
				boolean isStandardSequence = orbSequence.get(2) == OrbType.MELEE;

				// If it's a standard sequence, abbreviate even with MM3
				if (isStandardSequence)
				{
					return String.valueOf(orbSequence.get(0).code);
				}
				else
				{
					// Non-standard sequence - always return full sequence
					// (MM3 must be active for non-standard sequences to occur)
					StringBuilder sb = new StringBuilder();
					for (OrbType orb : orbSequence)
					{
						sb.append(orb.code);
					}
					return sb.toString();
				}
			}

			// If MM3 is active but sequence not complete, return "u"
			if (isMantimayhem3Active && !sequenceComplete)
			{
				return "u";
			}

			// Without MM3 or with incomplete sequence and no MM3, return first orb
			return orbSequence.isEmpty() ? "u" : String.valueOf(orbSequence.get(0).code);
		}

		boolean isCharged()
		{
			return charged;
		}

		ManticoreData copy()
		{
			ManticoreData copy = new ManticoreData();
			copy.charged = this.charged;
			copy.orbSequence = new ArrayList<>(this.orbSequence);
			copy.sequenceComplete = this.sequenceComplete;
			return copy;
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
		log.debug("Manticore {} current suffix: {}, charged: {}, sequence: {}, complete: {}",
			npcIndex, suffix, data.charged, data.orbSequence, data.sequenceComplete);
		return suffix;
	}

	public String getManticoreSpawnLosSuffix(int npcIndex, boolean isReinforcement)
	{
		ManticoreData capturedState = isReinforcement ? reinforcementSpawnStates.get(npcIndex) : initialSpawnStates.get(npcIndex);
		ManticoreData currentState = manticores.get(npcIndex);

		if (capturedState == null && currentState == null)
		{
			return "u";
		}

		boolean isMM3Active = isMantimayhem3Active();

		// If it was uncharged at spawn but now we know its pattern
		if (capturedState != null && !capturedState.isCharged() && currentState != null && currentState.isCharged())
		{
			String suffix = currentState.getLosSuffix(isMM3Active);
			// Only add "u" prefix if we have a valid pattern (not another "u")
			if (!suffix.equals("u"))
			{
				String result = "u" + suffix;
				log.debug("Manticore {} was uncharged at {}, now charged: {}",
					npcIndex, isReinforcement ? "reinforcement" : "spawn", result);
				return result; // e.g., "ur", "um", "urlm"
			}
		}

		// If it was charged at spawn
		if (capturedState != null && capturedState.isCharged())
		{
			// With MM3, we might have captured a partial pattern but now have the complete pattern
			if (isMM3Active && currentState != null && currentState.sequenceComplete && !capturedState.sequenceComplete)
			{
				// Use the current complete pattern instead of the captured partial pattern
				String suffix = currentState.getLosSuffix(isMM3Active);
				log.debug("Manticore {} was partially charged at {}, now complete: {}",
					npcIndex, isReinforcement ? "reinforcement" : "spawn", suffix);
				return suffix;
			}
			
			String result = capturedState.getLosSuffix(isMM3Active);
			log.debug("Manticore {} was charged at {}: {}",
				npcIndex, isReinforcement ? "reinforcement" : "spawn", result);
			return result;
		}

		// Otherwise use current state if available
		if (currentState != null)
		{
			String result = currentState.getLosSuffix(isMM3Active);
			log.debug("Manticore {} using current state for {}: {}",
				npcIndex, isReinforcement ? "reinforcement" : "spawn", result);
			return result;
		}

		return "u";
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
		// With MM3, need complete sequence
		return !isMantimayhem3Active() ? data.isCharged() : data.sequenceComplete;
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
		initialSpawnStates.clear();
		reinforcementSpawnStates.clear();
	}

	public void captureSpawnStates(boolean isReinforcement)
	{
		Map<Integer, ManticoreData> targetMap = isReinforcement ? reinforcementSpawnStates : initialSpawnStates;
		targetMap.clear();

		for (Map.Entry<Integer, ManticoreData> entry : manticores.entrySet())
		{
			ManticoreData data = entry.getValue().copy();
			targetMap.put(entry.getKey(), data);
			log.debug("Captured {} spawn state for manticore {}: charged={}, sequence={}",
				isReinforcement ? "reinforcement" : "initial",
				entry.getKey(),
				data.charged,
				data.orbSequence);
		}
	}

	public void onNpcSpawned(NPC npc)
	{
		int index = npc.getIndex();
		manticores.put(index, new ManticoreData());
	}

	public void ensureManticoreTracked(NPC npc)
	{
		int index = npc.getIndex();
		// Only add if not already tracked
		if (!manticores.containsKey(index))
		{
			manticores.put(index, new ManticoreData());
			log.debug("Added manticore {} to tracking (was not caught by spawn event)", index);
		}
	}

	public void onNpcDespawned(NPC npc)
	{
		int index = npc.getIndex();
		manticores.remove(index);
	}

	public void checkNPCGraphics(NPC npc)
	{
		int graphic = npc.getGraphic();
		if (graphic <= 0)
		{
			return;
		}

		if (graphic == MAGIC_ORB_GRAPHIC_ID || graphic == RANGED_ORB_GRAPHIC_ID || graphic == MELEE_ORB_GRAPHIC_ID)
		{
			int index = npc.getIndex();
			ManticoreData data = manticores.get(index);
			if (data == null)
			{
				return;
			}

			// Only track sequence if we haven't completed it yet
			if (data.sequenceComplete)
			{
				log.debug("Manticore {} already has complete sequence, skipping", index);
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
				if (data.orbSequence.isEmpty() || data.orbSequence.get(data.orbSequence.size() - 1) != orbType)
				{
					data.orbSequence.add(orbType);
					data.charged = true;

					log.debug("Manticore {} orb sequence: {} (MM3: {})", index, data.orbSequence, mantimayhem3Active);

					// Check if we have a complete sequence
					if (data.orbSequence.size() >= 3)
					{
						data.sequenceComplete = true;
						log.debug("Manticore {} sequence complete: {} (MM3: {})", index, data.orbSequence, mantimayhem3Active);
					}
				}
			}
		}
	}
}