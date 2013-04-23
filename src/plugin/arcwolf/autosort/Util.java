package plugin.arcwolf.autosort;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.Hopper;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import plugin.arcwolf.autosort.Network.SortChest;

public class Util {

    private AutoSort plugin;

    public Util(AutoSort plugin) {
        this.plugin = plugin;
    }

    public boolean isValidInventoryBlock(Block block) {
        return isValidInventoryBlock(null, block, false);
    }

    public boolean isValidInventoryBlock(Player player, Block block, Boolean isEventCheck) {
        if (block.getType().equals(Material.CHEST) || block.getType().equals(Material.TRAPPED_CHEST) ||
                block.getType().equals(Material.DISPENSER) || block.getType().equals(Material.DROPPER) ||
                block.getType().equals(Material.HOPPER)) { return true; }
        if (isEventCheck) player.sendMessage(ChatColor.RED + "That's not recognised inventory block!");
        return false;
    }

    public boolean isValidDepositWithdrawBlock(Block block) {
        return isValidDepositWithdrawBlock(null, block, false);
    }

    public boolean isValidDepositWithdrawBlock(Player player, Block block, Boolean isEventCheck) {
        if (block.getType().equals(Material.CHEST) || block.getType().equals(Material.TRAPPED_CHEST) ||
                block.getType().equals(Material.FURNACE) || block.getType().equals(Material.BURNING_FURNACE) ||
                block.getType().equals(Material.HOPPER)) { return true; }
        if (isEventCheck) player.sendMessage(ChatColor.RED + "That's not recognised inventory block!");
        return false;
    }

    public static ItemStack parseMaterialID(String str) {
        if (str != null) {
            if (str.contains(":")) {
                String[] parts = str.split(":");
                String sid = parts[0];
                String sdam = parts[1];
                if (isNumeric(sid) && isNumeric(sdam)) {
                    int id = Integer.parseInt(sid);
                    short dam = Short.parseShort(sdam);
                    Material mat = Material.getMaterial(id);
                    if (mat != null) {
                        if (dam == 0) {
                            return new ItemStack(mat, 1);
                        }
                        else {
                            return new ItemStack(mat, 1, dam);
                        }
                    }
                }
            }
            else if (isNumeric(str)) {
                Material mat = Material.getMaterial(Integer.parseInt(str));
                if (mat != null) { return new ItemStack(mat, 1); }
            }
            else if (str.equalsIgnoreCase("MISC")) {
                return new ItemStack(Material.AIR);
            }
            else {
                Material mat = Material.getMaterial(str);
                if (mat != null) { return new ItemStack(mat, 1); }
            }
        }
        return null;
    }

    public static boolean isNumeric(String str) {
        if (str.equalsIgnoreCase("")) { return false; }
        if (str.contains(",")) { return false; }
        NumberFormat formatter = NumberFormat.getInstance();
        ParsePosition pos = new ParsePosition(0);
        formatter.parse(str, pos);
        return str.length() == pos.getIndex();
    }

    public static InventoryHolder getInventoryHolder(Block block) {
        InventoryHolder invHolder = null;
        if (block.getChunk().load()) {
            BlockState state = block.getState();
            if (state == null) {
                return null;
            }
            else if (state instanceof Dispenser) {
                invHolder = (Dispenser) state;
            }
            else if (state instanceof Chest) {
                invHolder = (Chest) state;
            }
            else if (state instanceof Dropper) {
                invHolder = (Dropper) state;
            }
            else if (state instanceof Hopper) {
                invHolder = (Hopper) state;
            }
        }
        return invHolder;
    }

    public Block findSign(Block block) {
        BlockFace[] surchest = { BlockFace.SELF, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
        for(BlockFace face : surchest) {
            Block sign = block.getRelative(face);
            if (sign.getType().equals(Material.WALL_SIGN) || sign.getType().equals(Material.SIGN_POST)) { return sign; }
        }
        return null;
    }

    public Inventory getInventory(Block block) {
        if (block.getState() instanceof Chest)
            return ((Chest) block.getState()).getInventory();
        else if (block.getState() instanceof Dispenser) {
            return ((Dispenser) block.getState()).getInventory();
        }
        else if (block.getState() instanceof Dropper) {
            return ((Dropper) block.getState()).getInventory();
        }
        else if (block.getState() instanceof Hopper) {
            return ((Hopper) block.getState()).getInventory();
        }
        else
            return null;
    }

    public Block doubleChest(Block block) {
        if (block.getType().equals(Material.CHEST) || block.getType().equals(Material.TRAPPED_CHEST)) {
            BlockFace[] surchest = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
            for(BlockFace face : surchest) {
                Block otherHalf = block.getRelative(face);
                if (otherHalf.getType().equals(block.getType())) { return otherHalf; }
            }
        }
        return block;
    }

    // Roll through the network and pull out the correct amount of resources.
    // If not enough space return a false
    // true is successful
    public boolean makeWithdraw(Player player, CustomPlayer settings) {
        int wantedAmount = settings.wantedAmount;
        int wantedItem = settings.inventory.get(settings.currentItemIdx).itemId;
        int wantedItemId = settings.inventory.get(settings.currentItemIdx).itemData;
        Map<Integer, ItemStack> couldntFit = null;
        Inventory networkInv;
        for(SortChest chest : settings.sortNetwork.sortChests) {
            if (!chest.block.getChunk().isLoaded())
                chest.block.getChunk().load();
            networkInv = getInventory(chest.block);
            if (networkInv == null) return false;
            for(int idx = 0; idx < networkInv.getSize(); idx++) {
                ItemStack networkItem = networkInv.getItem(idx);
                if (networkItem != null) {
                    if (networkItem.getTypeId() == wantedItem && networkItem.getData().getData() == wantedItemId) {
                        int foundAmount = networkItem.getAmount();
                        Inventory withdrawInv = settings.withdrawInventory;
                        ItemStack stack = networkItem;
                        if (wantedAmount >= foundAmount && foundAmount != 0) { // Found amount and was less then wanted
                            couldntFit = withdrawInv.addItem(stack);
                            if (couldntFit != null && !couldntFit.isEmpty()) { return false; }
                            wantedAmount -= foundAmount;
                            settings.wantedAmount = wantedAmount;
                            networkInv.clear(idx);
                        }
                        else if (wantedAmount != 0 && wantedAmount < foundAmount) { // Found amount and was more then wanted
                            while (wantedAmount > 0) {
                                couldntFit = withdrawInv.addItem(stack);
                                if (couldntFit != null && !couldntFit.isEmpty()) { return false; }
                                wantedAmount -= foundAmount;
                                settings.wantedAmount = wantedAmount;
                                networkInv.clear(idx);
                            }
                            if (couldntFit != null && !couldntFit.isEmpty()) { return false; }
                            wantedAmount -= foundAmount;
                            settings.wantedAmount = wantedAmount;
                        }
                    }
                }
            }
        }
        settings.wantedAmount = wantedAmount;
        return true;
    }

    public void updateChestInventory(Player player, CustomPlayer settings) {
        Chest chest = null;
        if (settings.block != null) {
            chest = (Chest) settings.block.getState();
        }
        ItemStack dummyItem = new ItemStack(373, 1);
        try {
            if (settings.block != null && !settings.block.getChunk().isLoaded())
                settings.block.getChunk().load();
            if (tooManyItems(player, settings)) player.sendMessage(ChatColor.GOLD + settings.netName + ChatColor.RED + " is too full to replace withdrawchest Items!");
            settings.withdrawInventory.clear();
            settings.withdrawInventory.setItem(0, dummyItem);
            settings.withdrawInventory.setItem(8, dummyItem);

            for(settings.currentItemIdx = settings.startItemIdx; settings.currentItemIdx < settings.inventory.size(); settings.currentItemIdx++) {
                settings.wantedAmount = settings.inventory.get(settings.currentItemIdx).amount;
                makeWithdraw(player, settings);
            }
            if (settings.withdrawInventory.firstEmpty() != -1 && settings.startItemIdx != 0) {
                for(int count = 0; count < settings.startItemIdx; count++) {
                    settings.currentItemIdx = count;
                    settings.wantedAmount = settings.inventory.get(count).amount;
                    makeWithdraw(player, settings);
                }
            }
        } catch (Exception e) {
            ConsoleCommandSender sender = plugin.getServer().getConsoleSender();
            sender.sendMessage(ChatColor.RED + "AutoSort critical Withdraw Chest error!");
            sender.sendMessage(chest != null ? "Chest at " + chest.getLocation() : "Player at " + player.getLocation());
            sender.sendMessage("Player was " + player.getName());
            sender.sendMessage("Owner was " + settings.owner);
            sender.sendMessage("Network was " + settings.netName);
            sender.sendMessage("Error is as follows: ");
            sender.sendMessage(ChatColor.RED + "---------------------------------------");
            e.printStackTrace();
            sender.sendMessage(ChatColor.RED + "---------------------------------------");
        } finally {
            settings.withdrawInventory.setItem(0, new ItemStack(0));
            settings.withdrawInventory.setItem(8, new ItemStack(0));
        }
    }

    public boolean tooManyItems(Player player, CustomPlayer settings) {
        boolean tooManyItems = false;
        Inventory inv = settings.withdrawInventory;
        Location dropLoc = player.getLocation();
        for(int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) != null) {
                if (!settings.sortNetwork.sortItem(inv.getItem(i))) {
                    dropLoc.getWorld().dropItem(dropLoc, inv.getItem(i));
                    tooManyItems = true;
                }
            }
        }
        return tooManyItems;
    }

    public boolean updateInventoryList(Player player, CustomPlayer settings) {
        for(SortChest chest : settings.sortNetwork.sortChests) {
            Inventory inv = Util.getInventoryHolder(chest.block).getInventory();
            if (inv == null) continue;
            for(ItemStack item : inv) {
                if (item != null) {
                    int itemId = item.getTypeId();
                    int itemData = item.getData().getData();
                    int index = settings.findItem(itemId, itemData);
                    if (index != -1) {
                        settings.inventory.get(index).amount += item.getAmount();
                    }
                    else {
                        settings.inventory.add(new InventoryItem(itemId, itemData, item.getAmount()));
                    }
                }
            }
        }
        return settings.inventory.size() > 0;
    }

    public void updateChestTask(final Player player, final CustomPlayer settings) {
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

            public void run() {
                plugin.util.updateChestInventory(player, settings);
            }
        }, 3);
    }

    public void restoreWithdrawnInv(CustomPlayer settings, Player player) {
        if (settings.sortNetwork != null) {
            if (plugin.asListener.chestLock.containsKey(player.getName())) {
                plugin.asListener.chestLock.remove(player.getName());
                Inventory inv = settings.withdrawInventory;
                for(int i = 0; i < inv.getSize(); i++) {
                    if (inv.getItem(i) != null) {
                        settings.sortNetwork.sortItem(inv.getItem(i));
                    }
                }
                inv.clear();
                settings.clearPlayer();
            }
        }
    }
}
