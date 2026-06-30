package ru.trapkit.trap.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.trapkit.trap.TrapPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Отвечает за постройку временной комнаты 3x3x3 вокруг игрока и
 * её точное восстановление (блок-в-блок) по истечении времени —
 * мир не остаётся деформированным ни на один блок.
 */
public class RoomManager {

    private final TrapPlugin plugin;
    private final Map<UUID, ActiveRoom> activeRooms = new ConcurrentHashMap<>();

    public RoomManager(TrapPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isTrapped(Player player) {
        return activeRooms.containsKey(player.getUniqueId());
    }

    public void trapPlayer(Player target) {
        if (isTrapped(target)) {
            return;
        }

        Block feet = target.getLocation().getBlock();
        World world = feet.getWorld();

        Material cornerMat = matFromConfig("trap.materials.corner", Material.CHISELED_POLISHED_BLACKSTONE);
        Material wallMat = matFromConfig("trap.materials.wall", Material.POLISHED_BLACKSTONE);
        Material stairMat = matFromConfig("trap.materials.stair", Material.POLISHED_BLACKSTONE_STAIRS);

        int baseX = feet.getX();
        int baseY = feet.getY();
        int baseZ = feet.getZ();

        List<SavedBlock> saved = new ArrayList<>();

        // Центр на уровне головы (dy = 1, dx = 0, dz = 0) оставляем
        // полым — это единственная "пустая" точка внутри комнаты,
        // все остальные 26 блоков куба 3x3x3 формируют поверхность комнаты.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {

                    if (dx == 0 && dy == 1 && dz == 0) {
                        continue;
                    }

                    boolean isXEdge = (dx == -1 || dx == 1);
                    boolean isZEdge = (dz == -1 || dz == 1);
                    boolean isYEdge = (dy == 0 || dy == 2);

                    if (!isXEdge && !isZEdge && !isYEdge) {
                        continue;
                    }

                    Block block = world.getBlockAt(baseX + dx, baseY + dy, baseZ + dz);
                    saved.add(new SavedBlock(block.getLocation(), block.getBlockData()));

                    boolean isCorner = isXEdge && isZEdge && isYEdge;
                    boolean isHorizontalEdgeOnFloorOrCeiling =
                            isYEdge && (isXEdge ^ isZEdge); // ровно одна из горизонтальных осей на краю

                    BlockData newData;
                    if (isCorner) {
                        newData = cornerMat.createBlockData();
                    } else if (isHorizontalEdgeOnFloorOrCeiling) {
                        newData = buildStairData(stairMat, dy, dx, dz, isXEdge);
                    } else {
                        newData = wallMat.createBlockData();
                    }

                    block.setBlockData(newData, false);
                }
            }
        }

        int durationTicks = plugin.getConfig().getInt("trap.duration-seconds", 10) * 20;

        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> restore(target.getUniqueId()),
                Math.max(1L, durationTicks)
        );

        activeRooms.put(target.getUniqueId(), new ActiveRoom(saved, task));
    }

    private BlockData buildStairData(Material stairMat, int dy, int dx, int dz, boolean isXEdge) {
        BlockData data = stairMat.createBlockData();
        if (data instanceof Stairs stairs) {
            stairs.setHalf(dy == 0 ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
            stairs.setShape(Stairs.Shape.STRAIGHT);
            stairs.setFacing(outwardFace(dx, dz, isXEdge));
            return stairs;
        }
        return data;
    }

    private BlockFace outwardFace(int dx, int dz, boolean isXEdge) {
        if (isXEdge) {
            return dx > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private void restore(UUID uuid) {
        ActiveRoom room = activeRooms.remove(uuid);
        if (room == null) {
            return;
        }
        // Восстанавливаем в обратном порядке, блок-в-блок,
        // без физических обновлений (false), чтобы исчезновение
        // комнаты было мгновенным и не вызывало побочных эффектов.
        for (int i = room.blocks.size() - 1; i >= 0; i--) {
            SavedBlock sb = room.blocks.get(i);
            Block block = sb.location.getBlock();
            block.setBlockData(sb.data, false);
        }
    }

    /**
     * Принудительно и немедленно восстанавливает все активные комнаты
     * (используется при отключении плагина/перезапуске сервера).
     */
    public void restoreAllImmediately() {
        for (UUID uuid : new ArrayList<>(activeRooms.keySet())) {
            ActiveRoom room = activeRooms.get(uuid);
            if (room != null && room.task != null) {
                room.task.cancel();
            }
            restore(uuid);
        }
    }

    private Material matFromConfig(String path, Material fallback) {
        String name = plugin.getConfig().getString(path, fallback.name());
        Material material = Material.matchMaterial(name);
        return material != null ? material : fallback;
    }

    private record SavedBlock(Location location, BlockData data) {
    }

    private record ActiveRoom(List<SavedBlock> blocks, BukkitTask task) {
    }
}
