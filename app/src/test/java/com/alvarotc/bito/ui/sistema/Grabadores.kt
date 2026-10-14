package com.alvarotc.bito.ui.sistema

import androidx.compose.runtime.Composable
import com.alvarotc.bito.ui.sistema.sonido.Bucle
import com.alvarotc.bito.ui.sistema.sonido.Canal
import com.alvarotc.bito.ui.sistema.sonido.MotorSonido
import com.alvarotc.bito.ui.sistema.sonido.Snd

/** Motor de sonido de test: apunta lo que suena. */
class SonidoGrabado : MotorSonido {
    val sonados = mutableListOf<Pair<Snd, Float>>()
    val probados = mutableListOf<Pair<Canal, Int>>()
    val bucles = mutableMapOf<Snd, BucleGrabado>()
    var segundosPlanos = 0

    fun snds(): List<Snd> = sonados.map { it.first }

    override fun play(
        snd: Snd,
        intensidad: Float,
    ) {
        sonados += snd to intensidad
    }

    override fun bucle(snd: Snd): Bucle = bucles.getOrPut(snd) { BucleGrabado() }

    override fun probar(
        canal: Canal,
        nivel: Int,
    ) {
        probados += canal to nivel
    }

    override fun ganancia(canal: Canal): Float = 1f

    override fun segundoPlano() {
        segundosPlanos++
    }
}

class BucleGrabado : Bucle {
    override var sonando: Boolean = false
        private set

    override fun empezar() {
        sonando = true
    }

    override fun parar() {
        sonando = false
    }
}

/** Tacto de test: apunta lo que vibra. */
class TactoGrabado : Tacto {
    val hapticas = mutableListOf<Haptica>()
    val probados = mutableListOf<Int>()

    override fun haptica(h: Haptica) {
        hapticas += h
    }

    override fun probar(nivel: Int) {
        probados += nivel
    }
}

/** El sistema con grabadores, para los tests de componentes y pantallas. */
@Composable
fun ConSistema(
    sonido: MotorSonido = SonidoGrabado(),
    tacto: Tacto = TactoGrabado(),
    reducido: Boolean = false,
    content: @Composable () -> Unit,
) = BitoSistema(reducirAnimaciones = reducido, sonido = sonido, tacto = tacto, content = content)
