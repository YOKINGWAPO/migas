package com.jokin.calorias.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseParserTest {
    private fun parse(text: String) = ExerciseParser.parse(text)

    @Test
    fun readsTheUserExample() {
        assertEquals(
            listOf(ParsedExercise("Flexiones", 4, 15, null), ParsedExercise("Dominadas", 4, 8, null)),
            parse("4x15 repeticiones de flexiones y 4x8 de dominadas"),
        )
    }

    @Test
    fun readsHowTheRecognizerWritesIt() {
        assertEquals(listOf(ParsedExercise("Flexiones", 4, 15, null)), parse("4 x 15 flexiones"))
        assertEquals(listOf(ParsedExercise("Flexiones", 4, 15, null)), parse("4 por 15 de flexiones"))
        assertEquals(listOf(ParsedExercise("Flexiones", 4, 15, null)), parse("He hecho 4 series de 15 flexiones"))
    }

    @Test
    fun readsNumbersInWords() {
        assertEquals(listOf(ParsedExercise("Flexiones", 4, 15, null)), parse("cuatro series de quince repeticiones de flexiones"))
        assertEquals(listOf(ParsedExercise("Sentadillas", 3, 20, null)), parse("tres por veinte sentadillas"))
    }

    @Test
    fun putsTheNameFirstToo() {
        assertEquals(listOf(ParsedExercise("Dominadas", 4, 8, null)), parse("Dominadas 4x8"))
    }

    @Test
    fun readsWeight() {
        assertEquals(listOf(ParsedExercise("Press banca", 3, 10, 40.0)), parse("3x10 press banca con 40 kilos"))
        assertEquals(listOf(ParsedExercise("Curl de biceps", 4, 12, 12.5)), parse("curl de bíceps 4 por 12 con 12,5 kg"))
    }

    @Test
    fun bareCountIsOneSet() {
        assertEquals(listOf(ParsedExercise("Flexiones", 1, 20, null)), parse("20 flexiones"))
    }

    @Test
    fun skipsWhatIsNotAnExercise() {
        assertEquals(emptyList<ParsedExercise>(), parse(""))
        assertEquals(emptyList<ParsedExercise>(), parse("flexiones"))
        assertEquals(emptyList<ParsedExercise>(), parse("4x15"))
        assertEquals(listOf(ParsedExercise("Dominadas", 4, 8, null)), parse("hoy fuerza y 4x8 dominadas"))
    }

    @Test
    fun rejectsImpossibleCounts() {
        assertEquals(emptyList<ParsedExercise>(), parse("0x15 flexiones"))
        assertEquals(emptyList<ParsedExercise>(), parse("4x0 flexiones"))
        assertEquals(emptyList<ParsedExercise>(), parse("51x10 flexiones"))
        assertEquals(listOf(ParsedExercise("Remo", 3, 10, null)), parse("3x10 remo con 0 kilos"))
    }

    @Test
    fun readsEnglishSetsOf() {
        assertEquals(listOf(ParsedExercise("Bench press", 3, 10, 60.0)), parse("bench press 3 sets of 10 with 60 kilos"))
        assertEquals(listOf(ParsedExercise("Squats", 3, 12, null)), parse("I did 3 sets of 12 squats"))
        assertEquals(listOf(ParsedExercise("Curls", 4, 12, 12.5)), parse("curls 4 times 12 at 12.5 kg"))
    }

    @Test
    fun readsSeveralEnglishExercises() {
        assertEquals(
            listOf(ParsedExercise("Push ups", 4, 15, null), ParsedExercise("Pull ups", 4, 8, null)),
            parse("4x15 push-ups and 4x8 pull-ups"),
        )
    }

    @Test
    fun readsEnglishNumbersInWords() {
        assertEquals(listOf(ParsedExercise("Squats", 3, 20, null)), parse("three by twenty squats"))
        assertEquals(listOf(ParsedExercise("Curls", 1, 10, null)), parse("ten reps of curls"))
    }

    @Test
    fun ignoresWeightsThatAreNotKilos() {
        assertEquals(emptyList<ParsedExercise>(), parse("bench press 3x10 with 60 pounds"))
    }
}
