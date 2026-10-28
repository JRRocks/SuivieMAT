package com.scanlist.app

import com.scanlist.app.ui.navigation.Routes
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigation retient le premier ecran dont le modele de route correspond a la route
 * demandee, pas forcement celui qui porte exactement ce nom. Une route fixe qu'un
 * modele a parametres avale envoie donc sur le mauvais ecran : c'est ce qui ouvrait
 * l'application sur un scan sans retour quand Nouveau scan s'appelait "scan/new".
 */
class RoutesTest {

    private val routes: List<String> = Routes::class.java.declaredFields
        .filter { it.type == String::class.java && !it.name.startsWith("ARG_") }
        .map { it.isAccessible = true; it.get(null) as String }

    /** Modele de route en expression reguliere : {arg} = un segment, requete ignoree. */
    private fun patternOf(route: String): Regex =
        Regex(
            route.substringBefore('?')
                .split('/')
                .joinToString("/") { if (it.startsWith("{")) "[^/]+" else Regex.escape(it) },
        )

    @Test
    fun `aucune route n en avale une autre`() {
        val collisions = routes.flatMap { route ->
            routes
                .filter { other ->
                    other != route && patternOf(other).matches(route.substringBefore('?'))
                }
                .map { other -> "$route correspond aussi a $other" }
        }
        assertTrue(collisions.joinToString("\n"), collisions.isEmpty())
    }

    @Test
    fun `l ecran d ouverture ne ressemble pas a un ecran de scan`() {
        assertTrue(!patternOf(Routes.SCAN).matches(Routes.NEW_SCAN_MODE))
    }
}
