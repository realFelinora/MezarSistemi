package dev.felinora.mezar;

import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/** Bir oyuncunun öldüğü yerde bekleyen eşyaları. */
final class Mezar {

    final UUID id;
    final UUID sahip;
    final String sahipAdi;
    final Location yer;
    final long zaman;
    /** Envanter slotu -> eşya (36-39 zırh, 40 sol el). */
    final Map<Integer, ItemStack> esyalar;
    final int xp;

    TextDisplay yazi;
    /** Gerçekçi mezar: ön yüzün baktığı yön (derece, 90'a yuvarlanmış) ve rastgelelik tohumu. */
    float yon;
    long tohum;
    final java.util.List<org.bukkit.entity.Display> gorseller = new java.util.ArrayList<>();
    final java.util.List<Location> alevler = new java.util.ArrayList<>();
    boolean animasyonda;
    /** /mezar ornek ile oluşturulan gösteri mezarı: içinde eşya yok, kaydedilmez. */
    boolean gosteri;
    org.bukkit.inventory.ItemStack gosteriSilah;

    Mezar(UUID id, UUID sahip, String sahipAdi, Location yer, long zaman, Map<Integer, ItemStack> esyalar, int xp) {
        this.id = id;
        this.sahip = sahip;
        this.sahipAdi = sahipAdi;
        this.yer = yer;
        this.zaman = zaman;
        this.esyalar = esyalar;
        this.xp = xp;
    }

    int esyaSayisi() {
        return esyalar.values().stream().mapToInt(ItemStack::getAmount).sum();
    }

    String anahtar() {
        return anahtar(yer);
    }

    static String anahtar(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }
}
