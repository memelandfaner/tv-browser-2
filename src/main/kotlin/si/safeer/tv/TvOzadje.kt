package si.safeer.tv

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream

/**
 * Ozadje domacega zaslona televizorja.
 *
 * Ta televizor (Philips z Androidom) nima nastavitve za ozadje - sliko postavi proizvajalec
 * in je ni mogoce zamenjati nikjer v sistemu. Kdor gleda zvecer v temni sobi, mu svetlo
 * ozadje sveti v oci, zato ga tu lahko na eno potezo zamenja s crnim.
 *
 * Dve pravili, da poteza ni nevarna:
 *  - prvotno ozadje shranimo, preden karkoli spremenimo, in ga znamo vrniti;
 *  - ce shranjene slike ni, se vrnemo na tovarnisko (WallpaperManager.clear()).
 *
 * Dovoljenje SET_WALLPAPER je navadno dovoljenje (brez vprasanja ob zagonu), sprememba pa se
 * zgodi samo takrat, kadar jo uporabnik sam izbere v meniju.
 */
object TvOzadje {

    private const val NASTAVITVE = "safeer_tv_ozadje"
    private const val KLJUC_CRNO = "crno_ozadje"
    private const val SHRANJENO = "prvotno_ozadje.png"

    /** Ali televizor sploh dovoli zamenjavo ozadja. */
    fun podprto(ctx: Context): Boolean = try {
        WallpaperManager.getInstance(ctx).isWallpaperSupported
    } catch (_: Throwable) {
        false
    }

    /** Ali je zdaj postavljeno nase crno ozadje. */
    fun jeCrno(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(NASTAVITVE, Context.MODE_PRIVATE).getBoolean(KLJUC_CRNO, false)
    } catch (_: Throwable) {
        false
    }

    private fun zapisiStanje(ctx: Context, crno: Boolean) {
        try {
            ctx.getSharedPreferences(NASTAVITVE, Context.MODE_PRIVATE)
                .edit().putBoolean(KLJUC_CRNO, crno).apply()
        } catch (_: Throwable) {
        }
    }

    /** Shrani trenutno ozadje, dokler ga se nismo zamenjali. Naredi se natanko enkrat. */
    private fun shraniPrvotno(ctx: Context) {
        val datoteka = File(ctx.filesDir, SHRANJENO)
        if (datoteka.exists()) return
        try {
            val risba = WallpaperManager.getInstance(ctx).drawable ?: return
            val sirina = if (risba.intrinsicWidth > 0) risba.intrinsicWidth else 1920
            val visina = if (risba.intrinsicHeight > 0) risba.intrinsicHeight else 1080
            val slika = Bitmap.createBitmap(sirina, visina, Bitmap.Config.ARGB_8888)
            val platno = Canvas(slika)
            risba.setBounds(0, 0, sirina, visina)
            risba.draw(platno)
            FileOutputStream(datoteka).use { slika.compress(Bitmap.CompressFormat.PNG, 100, it) }
            slika.recycle()
        } catch (_: Throwable) {
            try { datoteka.delete() } catch (_: Throwable) {}
        }
    }

    /** Postavi crno ozadje. Vrne true, kadar je uspelo. */
    fun vklopiCrno(ctx: Context): Boolean = try {
        shraniPrvotno(ctx)
        val slika = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        slika.eraseColor(Color.BLACK)
        WallpaperManager.getInstance(ctx).setBitmap(slika)
        slika.recycle()
        zapisiStanje(ctx, true)
        true
    } catch (_: Throwable) {
        false
    }

    /** Vrne prvotno ozadje; ce ga nimamo shranjenega, tovarnisko. */
    fun povrni(ctx: Context): Boolean = try {
        val upravitelj = WallpaperManager.getInstance(ctx)
        val datoteka = File(ctx.filesDir, SHRANJENO)
        var vrnjeno = false
        if (datoteka.exists()) {
            val slika = BitmapFactory.decodeFile(datoteka.absolutePath)
            if (slika != null) {
                upravitelj.setBitmap(slika)
                slika.recycle()
                vrnjeno = true
            }
        }
        if (!vrnjeno) upravitelj.clear()
        zapisiStanje(ctx, false)
        true
    } catch (_: Throwable) {
        false
    }
}
