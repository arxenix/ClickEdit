package com.bobacadodl.ClickEdit;

/**
 * Created by Nisovin!
 */

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SignGUI implements Listener {

    protected ProtocolManager protocolManager;
    protected PacketAdapter packetListener;
    protected Map<UUID, SignGUIListener> listeners;
    protected Map<UUID, Vector> signLocations;

    public SignGUI(Plugin plugin){
        this(plugin,true);
    }

    public SignGUI(Plugin plugin, boolean cleanup) {
        protocolManager = ProtocolLibrary.getProtocolManager();
        packetListener = new PacketListener(plugin);
        protocolManager.addPacketListener(packetListener);
        listeners = new ConcurrentHashMap<UUID, SignGUIListener>();
        signLocations = new ConcurrentHashMap<UUID, Vector>();
        if(cleanup){
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, SignGUIListener response) {
        open(player, null, response);
    }


    public void open(Player player, String[] defaultText, SignGUIListener response) {
        List<PacketContainer> packets = new ArrayList<PacketContainer>();

        int x = 0, y = 0, z = 0;
        if (defaultText != null) {
            x = player.getLocation().getBlockX();
            z = player.getLocation().getBlockZ();

            PacketContainer blockChange = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);
            blockChange.getIntegers().write(0, x).write(1, y).write(2, z).write(3, 63).write(4, 0);
            packets.add(blockChange);

            PacketContainer updateSign = protocolManager.createPacket(PacketType.Play.Server.UPDATE_SIGN);
            updateSign.getIntegers().write(0, x).write(1, y).write(2, z);
            updateSign.getStringArrays().write(0, defaultText);
            packets.add(updateSign);
        }

        PacketContainer openSign = protocolManager.createPacket(PacketType.Play.Server.OPEN_SIGN_ENTITY);
        openSign.getIntegers().write(0, 0).write(1, x).write(2, y).write(3, z);
        packets.add(openSign);

        if (defaultText != null) {
            PacketContainer blockChange = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);
            blockChange.getIntegers().write(0, x).write(1, y).write(2, z).write(3, 7).write(4, 0);
            packets.add(blockChange);
        }

        try {
            for (PacketContainer packet : packets) {
                protocolManager.sendServerPacket(player, packet);
            }
            signLocations.put(player.getUniqueId(), new Vector(x, y, z));
            listeners.put(player.getUniqueId(), response);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void destroy() {
        protocolManager.removePacketListener(packetListener);
        listeners.clear();
        signLocations.clear();
    }

    public void cleanupPlayer(Player player){
        listeners.remove(player.getUniqueId());
        signLocations.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event){
        cleanupPlayer(event.getPlayer());
    }

    public interface SignGUIListener {
        public void onSignDone(Player player, String[] lines);
    }

    class PacketListener extends PacketAdapter {

        Plugin plugin;

        public PacketListener(Plugin plugin) {
            super(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.UPDATE_SIGN);
            this.plugin = plugin;
        }

        @Override
        public void onPacketReceiving(PacketEvent event) {
            final Player player = event.getPlayer();
            Vector v = signLocations.remove(player.getUniqueId());
            if (v == null) return;
            List<Integer> list = event.getPacket().getIntegers().getValues();
            if (list.get(0) != v.getBlockX()) return;
            if (list.get(1) != v.getBlockY()) return;
            if (list.get(2) != v.getBlockZ()) return;

            final String[] lines = event.getPacket().getStringArrays().getValues().get(0);
            final SignGUIListener response = listeners.remove(event.getPlayer().getUniqueId());
            if (response != null) {
                event.setCancelled(true);
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                    public void run() {
                        response.onSignDone(player, lines);
                    }
                });
            }
        }

    }

}