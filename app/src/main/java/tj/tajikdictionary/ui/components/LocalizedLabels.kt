package tj.tajikdictionary.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tj.tajikdictionary.R
import tj.tajikdictionary.domain.model.PartOfSpeech

@Composable
fun PartOfSpeech.localizedLabel(): String = when (this) {
    PartOfSpeech.NOUN -> stringResource(R.string.pos_n)
    PartOfSpeech.VERB -> stringResource(R.string.pos_v)
    PartOfSpeech.ADJECTIVE -> stringResource(R.string.pos_adj)
    PartOfSpeech.ADVERB -> stringResource(R.string.pos_adv)
    PartOfSpeech.PRONOUN -> stringResource(R.string.pos_pron)
    PartOfSpeech.NUMERAL -> stringResource(R.string.pos_num)
    PartOfSpeech.PREPOSITION -> stringResource(R.string.pos_prep)
    PartOfSpeech.CONJUNCTION -> stringResource(R.string.pos_conj)
    PartOfSpeech.INTERJECTION -> stringResource(R.string.pos_intj)
    PartOfSpeech.PARTICLE -> stringResource(R.string.pos_part)
    PartOfSpeech.PHRASE -> stringResource(R.string.pos_phr)
}

@Composable
fun localizedTopicName(topic: String): String = when (topic.lowercase()) {
    "adjectives" -> stringResource(R.string.topic_adjectives)
    "verbs" -> stringResource(R.string.topic_verbs)
    "nature" -> stringResource(R.string.topic_nature)
    "education" -> stringResource(R.string.topic_education)
    "time" -> stringResource(R.string.topic_time)
    "animals" -> stringResource(R.string.topic_animals)
    "food" -> stringResource(R.string.topic_food)
    "body" -> stringResource(R.string.topic_body)
    "family" -> stringResource(R.string.topic_family)
    "home" -> stringResource(R.string.topic_home)
    "travel" -> stringResource(R.string.topic_travel)
    "grammar" -> stringResource(R.string.topic_grammar)
    "colors" -> stringResource(R.string.topic_colors)
    "numbers" -> stringResource(R.string.topic_numbers)
    "pronouns" -> stringResource(R.string.topic_pronouns)
    "greetings" -> stringResource(R.string.topic_greetings)
    "language" -> stringResource(R.string.topic_language)
    "basics" -> stringResource(R.string.topic_basics)
    "abstract" -> stringResource(R.string.topic_abstract)
    "questions" -> stringResource(R.string.topic_questions)
    "places" -> stringResource(R.string.topic_places)
    "people" -> stringResource(R.string.topic_people)
    "health" -> stringResource(R.string.topic_health)
    "work" -> stringResource(R.string.topic_work)
    "feelings" -> stringResource(R.string.topic_feelings)
    else -> topic.replaceFirstChar { it.uppercase() }
}

@Composable
fun localizedStudyDuration(seconds: Int): String {
    if (seconds < 60) return stringResource(R.string.duration_seconds, maxOf(0, seconds))
    val minutes = seconds / 60
    if (minutes < 60) return stringResource(R.string.duration_minutes, minutes)
    val hours = minutes / 60
    val remMin = minutes % 60
    return stringResource(R.string.duration_hours_minutes, hours, remMin)
}
