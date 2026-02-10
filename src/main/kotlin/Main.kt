import com.kevin.multijugador.client.ClientMain
import com.kevin.multijugador.server.ServerMain

fun main() {
    println("1) Iniciar servidor")
    println("2) Iniciar cliente")
    print("Elige opción: ")

    when (readlnOrNull()?.trim()) {
        "1" -> ServerMain.main(emptyArray())
        "2" -> ClientMain.main(emptyArray())
        else -> println("Opción no válida")
    }
}