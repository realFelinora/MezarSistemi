package dev.felinora.mezar;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MezarSistemi extends JavaPlugin implements Listener, TabCompleter {

    /** Mezarın yerine konabileceği "yumuşak" bloklar. */
    private static final Set<Material> YUMUSAK = EnumSet.noneOf(Material.class);

    // 1.20.5'te adı değişen parçacıklar: hangi sürümde hangisi varsa o kullanılır.
    private static final Particle TOZ = parcacik("DUST", "REDSTONE");
    private static final Particle TOTEM = parcacik("TOTEM_OF_UNDYING", "TOTEM");

    static {
        // "SHORT_GRASS" 1.20.3+, "GRASS" daha eski sürümlerde çimen bitkisi
        for (String ad : new String[]{"SHORT_GRASS", "GRASS", "FERN", "DEAD_BUSH", "SNOW"}) {
            Material m = Material.getMaterial(ad);
            if (m != null && m.isBlock() && m != Material.GRASS_BLOCK) YUMUSAK.add(m);
        }
    }

    private static Particle parcacik(String... adlar) {
        for (String ad : adlar) {
            try {
                return Particle.valueOf(ad);
            } catch (IllegalArgumentException ignored) {
            }
        }
        throw new IllegalStateException("Parçacık bulunamadı: " + String.join("/", adlar));
    }

    private final Map<String, Mezar> mezarlar = new LinkedHashMap<>();
    /** Dünyası yüklenmemiş mezarlar kaybolmasın diye ham hâliyle saklanır. */
    private final Map<String, ConfigurationSection> yuklenemeyen = new HashMap<>();

    private File veriDosyasi;
    private NamespacedKey yaziEtiketi;
    private String dil;
    private int korumaSuresi;
    private Material mezarBlogu;
    private Material platformBlogu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        yaziEtiketi = new NamespacedKey(this, "mezar_yazi");
        veriDosyasi = new File(getDataFolder(), "mezarlar.yml");
        ayarlariYukle();
        mezarlariYukle();

        getServer().getPluginManager().registerEvents(this, this);
        var komut = getCommand("mezar");
        if (komut != null) {
            komut.setExecutor(this);
            komut.setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::guncelle, 20L, 10L);
        getLogger().info(mezarlar.size() + " mezar yüklendi.");
    }

    @Override
    public void onDisable() {
        kaydet();
        for (Mezar m : mezarlar.values()) if (m.yazi != null) m.yazi.remove();
    }

    // ------------------------------------------------------------------ ayarlar

    private void ayarlariYukle() {
        reloadConfig();
        dil = getConfig().getString("dil", "tr").toLowerCase();
        korumaSuresi = getConfig().getInt("koruma-suresi", 300);
        mezarBlogu = malzeme(getConfig().getString("mezar-blogu"), Material.PLAYER_HEAD);
        platformBlogu = malzeme(getConfig().getString("platform-blogu"), Material.COBBLESTONE);
    }

    private Material malzeme(String ad, Material varsayilan) {
        Material m = ad == null ? null : Material.matchMaterial(ad);
        if (m == null || !m.isBlock()) {
            getLogger().warning("Geçersiz blok: " + ad + ", " + varsayilan + " kullanılıyor.");
            return varsayilan;
        }
        return m;
    }

    static String renk(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    String metin(String anahtar, String... degisenler) {
        String s = getConfig().getString("mesajlar." + dil + "." + anahtar);
        if (s == null) s = getConfig().getString("mesajlar.tr." + anahtar, anahtar);
        for (int i = 0; i + 1 < degisenler.length; i += 2) s = s.replace(degisenler[i], degisenler[i + 1]);
        return s;
    }

    void mesaj(CommandSender kime, String anahtar, String... degisenler) {
        kime.sendMessage(renk(metin("onek") + metin(anahtar, degisenler)));
    }

    /** "Felinora" -> "Felinora'nın", "Ahmet" -> "Ahmet'in" (İngilizcede "Felinora's"). */
    String iyelik(String ad) {
        if (!dil.equals("tr")) return ad + "'s";
        String k = ad.toLowerCase(java.util.Locale.forLanguageTag("tr"));
        char sesli = 'e';
        for (int i = k.length() - 1; i >= 0; i--) {
            if ("aeıioöuü".indexOf(k.charAt(i)) >= 0) { sesli = k.charAt(i); break; }
        }
        String ek = switch (sesli) {
            case 'a', 'ı' -> "ın";
            case 'o', 'u' -> "un";
            case 'ö', 'ü' -> "ün";
            default -> "in";
        };
        boolean sesliyleBitiyor = !k.isEmpty() && "aeıioöuü".indexOf(k.charAt(k.length() - 1)) >= 0;
        return ad + "'" + (sesliyleBitiyor ? "n" : "") + ek;
    }

    // ------------------------------------------------------------------ ölüm

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void olum(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (e.getKeepInventory() || !p.hasPermission("mezar.kullan")) return;
        if (getConfig().getStringList("haric-dunyalar").contains(p.getWorld().getName())) return;

        Map<Integer, ItemStack> esyalar = new LinkedHashMap<>();
        ItemStack[] icerik = p.getInventory().getContents();
        for (int i = 0; i < icerik.length; i++) {
            ItemStack it = icerik[i];
            if (it == null || it.getType().isAir()) continue;
            if (it.containsEnchantment(Enchantment.VANISHING_CURSE)) continue; // zaten yok olur
            esyalar.put(i, it.clone());
        }
        int xp = getConfig().getBoolean("xp-sakla", true) ? toplamXp(p) : 0;
        if (esyalar.isEmpty() && xp <= 0) return;

        Location yer = guvenliYer(p.getLocation());
        if (yer == null) return; // yer bulunamadı: eşyalar normal düşer

        // Mezara giren eşyaları yere düşenlerden çıkar.
        for (ItemStack it : esyalar.values()) e.getDrops().remove(it);
        if (xp > 0) e.setDroppedExp(0);

        mezarBlokuKoy(yer.getBlock(), p);
        Mezar m = new Mezar(UUID.randomUUID(), p.getUniqueId(), p.getName(), yer,
                System.currentTimeMillis(), esyalar, Math.max(0, xp));
        mezarlar.put(m.anahtar(), m);
        kaydet();

        if (getConfig().getBoolean("simsek", true)) yer.getWorld().strikeLightningEffect(yer);
        mesaj(p, "olustu", "%x%", String.valueOf(yer.getBlockX()), "%y%", String.valueOf(yer.getBlockY()),
                "%z%", String.valueOf(yer.getBlockZ()), "%dunya%", yer.getWorld().getName());
    }

    /** Oyuncunun o anki toplam XP puanı (level + bar). */
    static int toplamXp(Player p) {
        int l = p.getLevel();
        int temel = l <= 16 ? l * l + 6 * l
                : l <= 31 ? (int) (2.5 * l * l - 40.5 * l + 360)
                : (int) (4.5 * l * l - 162.5 * l + 2220);
        return temel + Math.round(p.getExp() * p.getExpToLevel());
    }

    private void mezarBlokuKoy(Block b, Player p) {
        b.setType(mezarBlogu, false);
        if (b.getState() instanceof Skull kafa) {
            kafa.setOwningPlayer(p);
            kafa.update(true, false);
        }
        BlockData veri = b.getBlockData();
        if (veri instanceof Rotatable r) {
            r.setRotation(p.getFacing().getOppositeFace());
            b.setBlockData(r, false);
        }
    }

    /**
     * Ölünen yerin yakınında, altı sağlam olan boş bir blok arar.
     * Lav ya da suda ölünce kıyıya, boşluğa düşünce yukarıya koyar.
     */
    private Location guvenliYer(Location olum) {
        World w = olum.getWorld();
        int min = w.getMinHeight() + 1, max = w.getMaxHeight() - 2;
        int x = olum.getBlockX(), z = olum.getBlockZ(), y = olum.getBlockY();

        if (y < min) { // boşluğa düştü
            Block ust = w.getHighestBlockAt(x, z);
            y = ust.getType().isAir() ? Math.max(min, Math.min(64, max)) : ust.getY() + 1;
        }
        y = Math.max(min, Math.min(max, y));

        for (int r = 0; r <= 6; r++) {
            for (int k = 0; k <= 12; k++) {
                int dy = (k % 2 == 0) ? k / 2 : -(k + 1) / 2; // 0, -1, 1, -2, 2 ...
                int yy = y + dy;
                if (yy < min || yy > max) continue;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        Block b = w.getBlockAt(x + dx, yy, z + dz);
                        if (bos(b) && zemin(b.getRelative(BlockFace.DOWN))) return b.getLocation();
                    }
                }
            }
        }
        // Yakında zemin yok (açık deniz, boşluk): yukarıdaki ilk boşluğa koy, altına platform yap.
        for (int yy = y; yy <= max; yy++) {
            Block b = w.getBlockAt(x, yy, z);
            if (!bos(b)) continue;
            Block alt = b.getRelative(BlockFace.DOWN);
            if (!zemin(alt) && !mezarlar.containsKey(Mezar.anahtar(alt.getLocation()))) alt.setType(platformBlogu);
            return b.getLocation();
        }
        return null;
    }

    private boolean bos(Block b) {
        return (b.getType().isAir() || YUMUSAK.contains(b.getType()))
                && !mezarlar.containsKey(Mezar.anahtar(b.getLocation()));
    }

    private boolean zemin(Block b) {
        return b.getType().isSolid() && !mezarlar.containsKey(Mezar.anahtar(b.getLocation()));
    }

    // ------------------------------------------------------------------ açma

    @EventHandler(priority = EventPriority.HIGH)
    public void tikla(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        Mezar m = mezarlar.get(Mezar.anahtar(b.getLocation()));
        if (m == null) return;
        e.setCancelled(true);
        ac(e.getPlayer(), m);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void kir(BlockBreakEvent e) {
        Mezar m = mezarlar.get(Mezar.anahtar(e.getBlock().getLocation()));
        if (m == null) return;
        e.setCancelled(true); // kırmaya çalışmak da açmak sayılır
        ac(e.getPlayer(), m);
    }

    private long kalanMs(Mezar m) {
        if (korumaSuresi < 0) return Long.MAX_VALUE;
        return m.zaman + korumaSuresi * 1000L - System.currentTimeMillis();
    }

    private void ac(Player p, Mezar m) {
        boolean sahibi = p.getUniqueId().equals(m.sahip);
        if (!sahibi && !p.hasPermission("mezar.yonetici")) {
            long kalan = kalanMs(m);
            if (kalan > 0) {
                if (korumaSuresi < 0) mesaj(p, "hep-korumali", "%oyuncu%", m.sahipAdi);
                else mesaj(p, "baskasinin", "%oyuncu%", m.sahipAdi, "%sure%", sure(kalan));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
        }

        mezariKaldir(m);

        PlayerInventory env = p.getInventory();
        List<ItemStack> artan = new ArrayList<>();
        for (Map.Entry<Integer, ItemStack> g : m.esyalar.entrySet()) {
            ItemStack mevcut = env.getItem(g.getKey());
            // Sahibi alırsa her şey eski yerine döner, zırh direkt giyilir.
            if (sahibi && (mevcut == null || mevcut.getType().isAir())) env.setItem(g.getKey(), g.getValue());
            else artan.add(g.getValue());
        }
        for (ItemStack it : artan) {
            env.addItem(it).values().forEach(kalan -> p.getWorld().dropItemNaturally(p.getLocation(), kalan));
        }
        if (m.xp > 0) p.giveExp(m.xp);

        Location orta = m.yer.clone().add(0.5, 0.4, 0.5);
        World w = orta.getWorld();
        w.spawnParticle(TOTEM, orta, 80, 0.3, 0.5, 0.3, 0.5);
        w.spawnParticle(Particle.SOUL, orta, 15, 0.3, 0.3, 0.3, 0.05);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        mesaj(p, "geri-geldi");
    }

    private void mezariKaldir(Mezar m) {
        mezarlar.remove(m.anahtar());
        if (m.yazi != null) m.yazi.remove();
        Block b = m.yer.getBlock();
        if (b.getType() == mezarBlogu) b.setType(Material.AIR, false);
        kaydet();
    }

    // ------------------------------------------------------------------ koruma

    private boolean mezarMi(Block b) {
        return mezarlar.containsKey(Mezar.anahtar(b.getLocation()));
    }

    @EventHandler
    public void patlama(EntityExplodeEvent e) {
        e.blockList().removeIf(this::mezarMi);
    }

    @EventHandler
    public void patlama(BlockExplodeEvent e) {
        e.blockList().removeIf(this::mezarMi);
    }

    @EventHandler
    public void piston(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(this::mezarMi)) e.setCancelled(true);
    }

    @EventHandler
    public void piston(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(this::mezarMi)) e.setCancelled(true);
    }

    @EventHandler
    public void akinti(BlockFromToEvent e) {
        if (mezarMi(e.getToBlock())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ yazı ve efektler

    static String sure(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private String durum(Mezar m) {
        if (korumaSuresi < 0) return metin("durum-hep");
        long kalan = kalanMs(m);
        return kalan > 0 ? metin("durum-korumali", "%sure%", sure(kalan)) : metin("durum-acik");
    }

    private int tik = 0;

    private void guncelle() {
        tik++;
        for (Mezar m : mezarlar.values()) {
            World w = m.yer.getWorld();
            if (w == null || !w.isChunkLoaded(m.yer.getBlockX() >> 4, m.yer.getBlockZ() >> 4)) continue;

            Block b = m.yer.getBlock();
            if (b.getType() != mezarBlogu) b.setType(mezarBlogu, false); // komutla silinirse geri koy

            String yazi = renk(metin("yazi-baslik", "%oyuncunun%", iyelik(m.sahipAdi)) + "\n"
                    + metin("yazi-icerik", "%esya%", String.valueOf(m.esyaSayisi()), "%xp%", String.valueOf(m.xp)) + "\n"
                    + durum(m) + "\n" + metin("yazi-ipucu"));
            if (m.yazi == null || !m.yazi.isValid()) {
                TextDisplay t = w.spawn(m.yer.clone().add(0.5, 0.75, 0.5), TextDisplay.class);
                t.setBillboard(Display.Billboard.CENTER);
                t.setPersistent(false);
                t.setShadowed(true);
                t.getPersistentDataContainer().set(yaziEtiketi, PersistentDataType.BYTE, (byte) 1);
                m.yazi = t;
            }
            m.yazi.setText(yazi);

            Location orta = m.yer.clone().add(0.5, 0.5, 0.5);
            w.spawnParticle(Particle.SOUL, orta.clone().add(0, 0.3, 0), 1, 0.15, 0.1, 0.15, 0.01);
            double a = tik * 0.35;
            w.spawnParticle(Particle.END_ROD, orta.clone().add(Math.cos(a) * 0.6, -0.3, Math.sin(a) * 0.6), 1, 0, 0, 0, 0);
        }
    }

    /** Oyuncunun önünden mezara doğru dönen altın sarmal, akan parıltılar ve mezarın üstünde ışık sütunu. */
    private void yolGoster(Player p, Mezar m) {
        Particle.DustOptions altin = new Particle.DustOptions(Color.fromRGB(255, 200, 40), 0.8f);
        Particle.DustOptions turuncu = new Particle.DustOptions(Color.fromRGB(255, 130, 20), 0.8f);
        Particle.DustTransition parilti = new Particle.DustTransition(Color.fromRGB(255, 225, 110), Color.WHITE, 1.7f);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        new BukkitRunnable() {
            int sayac = 0;

            @Override
            public void run() {
                if (!p.isOnline() || !mezarlar.containsKey(m.anahtar()) || !p.getWorld().equals(m.yer.getWorld())
                        || sayac++ > 100) { cancel(); return; } // ~10 saniye
                Location bas = p.getLocation().add(0, 1.0, 0);
                Location hedef = m.yer.clone().add(0.5, 0.5, 0.5);
                Vector yon = hedef.toVector().subtract(bas.toVector());
                double mesafe = yon.length();
                if (mesafe < 2) {
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
                    cancel();
                    return;
                }
                yon.normalize();
                Vector yukari = Math.abs(yon.getY()) > 0.95 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
                Vector u = yon.clone().crossProduct(yukari).normalize();
                Vector v = yon.clone().crossProduct(u).normalize();
                double son = Math.min(mesafe - 0.6, 14);
                double faz = sayac * 0.45;

                // Dönen çift sarmal (altın + turuncu)
                for (double d = 1.4; d < son; d += 0.45) {
                    double a = d * 2.0 - faz;
                    Vector ofs = u.clone().multiply(Math.cos(a) * 0.22).add(v.clone().multiply(Math.sin(a) * 0.22));
                    Location n = bas.clone().add(yon.clone().multiply(d));
                    p.spawnParticle(TOZ, n.clone().add(ofs), 1, 0, 0, 0, 0, altin);
                    p.spawnParticle(TOZ, n.clone().subtract(ofs), 1, 0, 0, 0, 0, turuncu);
                }
                // Mezara doğru akan parlak noktalar
                double kayma = (sayac * 0.3) % 1.8;
                for (double d = 1.4 + kayma; d < son; d += 1.8) {
                    p.spawnParticle(Particle.DUST_COLOR_TRANSITION, bas.clone().add(yon.clone().multiply(d)), 1, 0, 0, 0, 0, parilti);
                }
                // Rastgele ışıltılar
                if (sayac % 2 == 0) {
                    double d = 1.4 + Math.random() * Math.max(0.1, son - 1.4);
                    p.spawnParticle(Particle.WAX_ON, bas.clone().add(yon.clone().multiply(d)), 2, 0.25, 0.25, 0.25, 0);
                }
                // Mezarın üstünde etrafında altın sarmal dönen ışık sütunu
                if (mesafe < 48 && sayac % 2 == 0) {
                    for (double h = 0.5; h < 14; h += 0.35) {
                        double a = h * 1.3 + faz;
                        p.spawnParticle(TOZ, hedef.clone().add(Math.cos(a) * 0.4, h, Math.sin(a) * 0.4), 1, 0, 0, 0, 0, altin);
                    }
                    for (double h = 0.5; h < 14; h += 1.0) {
                        p.spawnParticle(Particle.END_ROD, hedef.clone().add(0, h, 0), 1, 0, 0, 0, 0);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    // ------------------------------------------------------------------ komut

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        String alt = args.length > 0 ? args[0].toLowerCase() : "";
        if (alt.equals("yenile") || alt.equals("reload")) {
            if (!s.hasPermission("mezar.yonetici")) { mesaj(s, "yetki-yok"); return true; }
            ayarlariYukle();
            mesaj(s, "yenilendi");
            return true;
        }
        if (alt.equals("liste") || alt.equals("list") || !(s instanceof Player)) {
            if (!s.hasPermission("mezar.yonetici")) { mesaj(s, "yetki-yok"); return true; }
            s.sendMessage(renk(metin("onek") + metin("yonetici-baslik", "%sayi%", String.valueOf(mezarlar.size()))));
            for (Mezar m : mezarlar.values()) {
                s.sendMessage(renk(metin("yonetici-satir", "%oyuncu%", m.sahipAdi,
                        "%x%", String.valueOf(m.yer.getBlockX()), "%y%", String.valueOf(m.yer.getBlockY()),
                        "%z%", String.valueOf(m.yer.getBlockZ()), "%dunya%", m.yer.getWorld().getName(),
                        "%durum%", durum(m))));
            }
            return true;
        }

        Player p = (Player) s;
        Location konum = p.getLocation();
        List<Mezar> benim = mezarlar.values().stream()
                .filter(m -> m.sahip.equals(p.getUniqueId()))
                .sorted(Comparator.comparingDouble(m -> m.yer.getWorld().equals(konum.getWorld())
                        ? m.yer.distanceSquared(konum) : Double.MAX_VALUE))
                .toList();
        if (benim.isEmpty()) { mesaj(p, "liste-bos"); return true; }

        p.sendMessage(renk(metin("onek") + metin("liste-baslik")));
        for (Mezar m : benim) {
            p.sendMessage(renk(metin("liste-satir",
                    "%x%", String.valueOf(m.yer.getBlockX()), "%y%", String.valueOf(m.yer.getBlockY()),
                    "%z%", String.valueOf(m.yer.getBlockZ()), "%dunya%", m.yer.getWorld().getName(),
                    "%durum%", durum(m))));
        }
        Mezar enYakin = benim.get(0);
        if (enYakin.yer.getWorld().equals(konum.getWorld())) {
            mesaj(p, "yol");
            yolGoster(p, enYakin);
        } else {
            mesaj(p, "baska-dunyada", "%dunya%", enYakin.yer.getWorld().getName());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 1 && s.hasPermission("mezar.yonetici")) {
            return List.of("liste", "yenile").stream().filter(a -> a.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }

    // ------------------------------------------------------------------ kayıt

    private void mezarlariYukle() {
        if (!veriDosyasi.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(veriDosyasi);
        ConfigurationSection kok = y.getConfigurationSection("mezarlar");
        if (kok == null) return;
        for (String id : kok.getKeys(false)) {
            ConfigurationSection k = kok.getConfigurationSection(id);
            if (k == null) continue;
            World w = Bukkit.getWorld(k.getString("dunya", ""));
            if (w == null) { yuklenemeyen.put(id, k); continue; }
            Map<Integer, ItemStack> esyalar = new LinkedHashMap<>();
            ConfigurationSection es = k.getConfigurationSection("esyalar");
            if (es != null) {
                for (String slot : es.getKeys(false)) {
                    ItemStack it = es.getItemStack(slot);
                    if (it != null) esyalar.put(Integer.parseInt(slot), it);
                }
            }
            Location yer = new Location(w, k.getInt("x"), k.getInt("y"), k.getInt("z"));
            Mezar m = new Mezar(UUID.fromString(id), UUID.fromString(k.getString("sahip")),
                    k.getString("ad", "?"), yer, k.getLong("zaman"), esyalar, k.getInt("xp"));
            mezarlar.put(m.anahtar(), m);
        }
    }

    private static Map<String, Object> haritaya(ConfigurationSection b) {
        Map<String, Object> h = new LinkedHashMap<>();
        for (Map.Entry<String, Object> g : b.getValues(false).entrySet()) {
            h.put(g.getKey(), g.getValue() instanceof ConfigurationSection alt ? haritaya(alt) : g.getValue());
        }
        return h;
    }

    private void kaydet() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, ConfigurationSection> h : yuklenemeyen.entrySet()) {
            y.createSection("mezarlar." + h.getKey(), haritaya(h.getValue()));
        }
        for (Mezar m : mezarlar.values()) {
            String k = "mezarlar." + m.id;
            y.set(k + ".sahip", m.sahip.toString());
            y.set(k + ".ad", m.sahipAdi);
            y.set(k + ".dunya", m.yer.getWorld().getName());
            y.set(k + ".x", m.yer.getBlockX());
            y.set(k + ".y", m.yer.getBlockY());
            y.set(k + ".z", m.yer.getBlockZ());
            y.set(k + ".zaman", m.zaman);
            y.set(k + ".xp", m.xp);
            for (Map.Entry<Integer, ItemStack> g : m.esyalar.entrySet()) {
                y.set(k + ".esyalar." + g.getKey(), g.getValue());
            }
        }
        try {
            y.save(veriDosyasi);
        } catch (IOException ex) {
            getLogger().severe("mezarlar.yml kaydedilemedi: " + ex.getMessage());
        }
    }
}
