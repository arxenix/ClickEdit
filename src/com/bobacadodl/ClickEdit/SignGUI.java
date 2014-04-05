package com.bobacadodl.ClickEdit;

/**
 * Created by Nisovin!
 * Updated to 1.7 by bobacadodl!
 * ProtocolLib by Comphenix
 */

import com.bobacadodl.ClickEdit.packetwrapper.WrapperPlayClientUpdateSign;
import com.bobacadodl.ClickEdit.packetwrapper.WrapperPlayServerBlockChange;
import com.bobacadodl.ClickEdit.packetwrapper.WrapperPlayServerOpenSignEntity;
import com.bobacadodl.ClickEdit.packetwrapper.WrapperPlayServerUpdateSign;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SignGUI implements Listener {

    protected ProtocolManager protocolManager;
    protected PacketAdapter packetListener;
    protected Map<UUID, SignGUIListener> listeners;
    protected Map<UUID, Vector> signLocations;

    public SignGUI(Plugin plugin) {
        this(plugin, true);
    }

    public SignGUI(Plugin plugin, boolean cleanup) {
        protocolManager = ProtocolLibrary.getProtocolManager();
        packetListener = new PacketListener(plugin);
        protocolManager.addPacketListener(packetListener);
        listeners = new ConcurrentHashMap<UUID, SignGUIListener>();
        signLocations = new ConcurrentHashMap<UUID, Vector>();
        if (cleanup) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, SignGUIListener response) {
        open(player, null, response);
    }


    public void open(Player player, String[] defaultText, SignGUIListener response) {

        Material block = player.getLocation().getBlock().getType();
        byte data = player.getLocation().getBlock().getData();
        //if setting pretext
        if (defaultText != null) {
            //set player location to sign block
            WrapperPlayServerBlockChange blockChange = new WrapperPlayServerBlockChange();
            blockChange.setLocation(player.getLocation());
            blockChange.setBlockType(Material.SIGN_POST);
            blockChange.sendPacket(player);

            //set sign to pretext
            WrapperPlayServerUpdateSign updateSign = new WrapperPlayServerUpdateSign();
            updateSign.setLocation(player.getLocation().getBlock().getLocation());
            updateSign.setLines(defaultText);
            updateSign.sendPacket(player);
        }

        //open dat sign
        WrapperPlayServerOpenSignEntity openSign = new WrapperPlayServerOpenSignEntity();
        openSign.setLocation(player.getLocation().getBlock().getLocation());
        openSign.sendPacket(player);

        //restore dat block and remove dat sign
        if (defaultText != null) {
            WrapperPlayServerBlockChange blockChange = new WrapperPlayServerBlockChange();
            blockChange.setLocation(player.getLocation());
            blockChange.setBlockType(block);
            blockChange.setBlockMetadata(data);
            blockChange.sendPacket(player);
        }

        //listen for da playa
        signLocations.put(player.getUniqueId(), player.getLocation().getBlock().getLocation().toVector());
        listeners.put(player.getUniqueId(), response);
    }

    public void destroy() {
        protocolManager.removePacketListener(packetListener);
        listeners.clear();
        signLocations.clear();
    }

    public void cleanupPlayer(Player player) {
        listeners.remove(player.getUniqueId());
        signLocations.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
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
            //updating dat sign
            WrapperPlayClientUpdateSign updateSign = new WrapperPlayClientUpdateSign(event.getPacket());


            final Player player = event.getPlayer();
            Vector v = signLocations.remove(player.getUniqueId());
            if (v == null) return;

            //make sure its dat sign
            if (updateSign.getX() != v.getBlockX()) return;
            if (updateSign.getY() != v.getBlockY()) return;
            if (updateSign.getZ() != v.getBlockZ()) return;

            final String[] lines = updateSign.getLines();
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