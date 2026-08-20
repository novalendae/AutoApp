package com.kaizen.auto.runtime

/** Lançada quando o script pede parada voluntária (scriptExit). */
class ScriptExitException(val exitMessage: String?) : RuntimeException(exitMessage ?: "scriptExit")

/** Lançada quando o usuário aperta PARAR. Não é erro — é fim normal. */
class ScriptStoppedException : RuntimeException("Script interrompido pelo usuário")

/** Equivalente ao FindFailed do Sikuli: padrão obrigatório não encontrado. */
class FindFailedException(val patternKey: String, message: String) : RuntimeException(message)
