package com.nflapp.data.local

import androidx.room.TypeConverter
import com.nflapp.data.remote.PastGameDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), Double.serializer())
    private val pastGames = ListSerializer(PastGameDto.serializer())

    @TypeConverter
    fun mapToString(value: Map<String, Double>): String = json.encodeToString(mapSerializer, value)

    @TypeConverter
    fun stringToMap(value: String): Map<String, Double> = json.decodeFromString(mapSerializer, value)

    @TypeConverter
    fun pastGamesToString(value: List<PastGameDto>): String = json.encodeToString(pastGames, value)

    @TypeConverter
    fun stringToPastGames(value: String): List<PastGameDto> = json.decodeFromString(pastGames, value)
}
