# MezarSistemi

Paper/Spigot sunucuları için **Türkçe mezar eklentisi**.
Oyuncu ölünce eşyaları yere saçılıp kaybolmaz, öldüğü yerde bir mezarda onu bekler.

**⬇️ İndir:** [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/graves-keep-items-and-xp-mezarsistemi) (önerilen) • [GitHub Releases](../../releases)

## Özellikler
- **Gerçekçi mezar (2.0):** Öldüğün yerde şimşek ve çan sesiyle topraktan bir mezar taşı yükselir. Taşta ✝ R.I.P, adın ve ölüm tarihin yazar; yosunlu, eskimiş ve hafif eğridir. Önünde çiçekli bir toprak yığını, yanan mumlar ve **en iyi kılıcın toprağa saplıdır**. Her mezar biraz farklıdır. Açınca mezar toprağa geri batar.
- **Eşyalar ve XP mezarda saklanır:** Levelin tamamı geri gelir
- **Tek tıkla geri al:** Eşyalar eski slotlarına döner, zırh direkt giyilir
- **Akıllı konum:** Lavda ya da suda ölünce mezar en yakın kıyıya, boşluğa düşünce güvenli bir yere konur
- **Hırsızlığa karşı koruma:** Mezarı belirli bir süre boyunca sadece sahibi açabilir (süre ayarlanabilir)
- **`/mezar` ile yol gösterme:** Altın parçacıklar seni mezarına götürür, mezarın üstünde ışık sütunu çıkar
- **Mezar üstünde yazı:** Sahibi, eşya sayısı, XP ve kalan koruma süresi
- Mezar kırılamaz, patlamalardan ve pistonlardan etkilenmez
- Sunucu kapansa bile mezarlar kaybolmaz
- **Türkçe ve İngilizce** dil desteği (`dil: tr` / `dil: en`)

## Komutlar
| Komut | Açıklama | Yetki |
|---|---|---|
| `/mezar` | Mezarlarını listeler ve en yakınına yol gösterir | `mezar.kullan` (herkes) |
| `/mezar liste` | Sunucudaki tüm mezarları listeler | `mezar.yonetici` (OP) |
| `/mezar yenile` | `config.yml` dosyasını yeniden yükler | `mezar.yonetici` |
| `/mezar ornek` | Baktığın yere içi boş bir gösteri mezarı yükseltir (deneme/tanıtım için) | `mezar.yonetici` |

`mezar.yonetici` yetkisi olanlar korumalı mezarları da açabilir.

## Kurulum
1. [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/graves-keep-items-and-xp-mezarsistemi) ya da [Releases](../../releases) kısmından `MezarSistemi-x.x.x.jar` dosyasını indirin.
2. Sunucunun `plugins` klasörüne atın ve sunucuyu yeniden başlatın.
3. İsterseniz `plugins/MezarSistemi/config.yml` dosyasını düzenleyip `/mezar yenile` yazın.

## Ayarlar
```yaml
dil: tr               # tr veya en
koruma-suresi: 300    # saniye, -1 = hep sadece sahibi açabilir
xp-sakla: true
simsek: true          # mezar oluşunca zararsız şimşek efekti
gorunum: gercekci     # gercekci veya kafa (eski oyuncu kafası görünümü)
tas-blogu: polished_andesite
silah-goster: true    # ölen oyuncunun kılıcı mezara saplı görünsün
mezar-blogu: PLAYER_HEAD  # sadece "kafa" görünümünde
haric-dunyalar: []    # mezar oluşmayacak dünyalar
```
Tüm mesajlar `config.yml` üzerinden değiştirilebilir.

**Uyumluluk:** Paper / Spigot 1.20.1 – 1.21.x, Java 17+

## Derleme
```sh
./gradlew build
# çıktı: build/libs/MezarSistemi-1.2.0.jar
```
