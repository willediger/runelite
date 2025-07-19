package net.runelite.client.plugins.colosseumwaves;

import net.runelite.api.Client;
import net.runelite.api.NPC;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Manticore handler for Colosseum Waves - tracks attack patterns via orb charging
 */
public class ManticoreHandler
{
    /* ── injected ── */
    private final Client client;
    private final ColosseumWavesPlugin plugin;

    /* ── static config ── */
    private static final int MANTICORE_NPC_ID = 12818;

    // Orb SpotanimIDs
    private static final int MAGIC_ORB = 2681;
    private static final int RANGED_ORB = 2683;
    private static final int MELEE_ORB = 2685;

    public ManticoreHandler(Client client, ColosseumWavesPlugin plugin)
    {
        this.client = client;
        this.plugin = plugin;
    }

    /* ── runtime ── */
    private final Map<NPC, MData> manticores = new HashMap<>();

    // Orb sequence tracking
    private final Map<NPC, List<Integer>> orbSequences = new HashMap<>();

    /* ── tiny helpers ── */
    private enum AType { MAGIC('m'), RANGED('r');
        final char suf; AType(char c){suf=c;} }

    private static class MData
    {
        AType first = null;
        char suf() { return first == AType.RANGED ? 'r' : 'm'; }
        boolean known() { return first != null; }
    }

    /* ── API used by main plugin ── */
    public char getManticoreLosSuffix(NPC n)
    {
        MData d = manticores.get(n);
        return d != null ? d.suf() : 'm';
    }

    public void clear()
    {
        manticores.clear();
        orbSequences.clear();
    }

    /* ── NPC life-cycle ── */
    public void onNpcSpawned(NPC n)
    {
        if (n.getId() == MANTICORE_NPC_ID)
        {
            manticores.put(n, new MData());
            orbSequences.put(n, new ArrayList<>());
        }
    }

    public void onNpcDespawned(NPC n)
    {
        manticores.remove(n);
        orbSequences.remove(n);
    }

    /* ── Track orbs via NPC graphic property ── */
    public void checkNPCGraphics(NPC npc)
    {
        if (npc == null || npc.getId() != MANTICORE_NPC_ID) return;

        int graphic = npc.getGraphic();
        if (graphic <= 0) return;

        // Check if this is an orb graphic
        if (graphic == MAGIC_ORB || graphic == RANGED_ORB || graphic == MELEE_ORB)
        {
            MData data = manticores.get(npc);
            if (data == null) return;

            List<Integer> sequence = orbSequences.get(npc);
            if (sequence == null) return;

            // Check if this is a new orb (not already in sequence)
            if (sequence.isEmpty() || sequence.get(sequence.size() - 1) != graphic)
            {
                sequence.add(graphic);

                // Detect pattern from first orb
                if (sequence.size() == 1 && data.first == null)
                {
                    if (graphic == MAGIC_ORB)
                    {
                        data.first = AType.MAGIC;
                    }
                    else if (graphic == RANGED_ORB)
                    {
                        data.first = AType.RANGED;
                    }
                }

                // Clear sequence after 3 orbs for next volley
                if (sequence.size() >= 3)
                {
                    sequence.clear();
                }
            }
        }
    }
}