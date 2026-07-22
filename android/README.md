# Agendador Claude (Android)

App Android que agenda o envio automático de mensagens dentro do **app oficial do
Claude**, sem usar a API. A automação abre o app, digita o texto e toca em enviar
usando um **Accessibility Service** — é uma automação de UI, não uma integração
oficial.

## ⚠️ Antes de usar

- **Frágil por natureza.** O app localiza o campo de texto e o botão de enviar
  navegando pela árvore de acessibilidade do app do Claude. Se o Anthropic mudar o
  layout do app, a automação pode parar de funcionar até o código ser ajustado.
  Isso é esperado — é um projeto de estudo/DIY, não algo para depender 100%.
- **Nome do pacote do Claude.** O app assume que o pacote instalado é
  `com.anthropic.claude` (veja `Constants.CLAUDE_PACKAGE_NAMES`). Se no seu
  aparelho for diferente, ajuste essa lista antes de compilar (Configurações >
  Apps > Claude > detalhes do app mostra o nome do pacote).
- **Permissão de Acessibilidade é manual.** O Android não permite conceder essa
  permissão via automação — na primeira execução, o app mostra um banner com
  atalho para a tela de Configurações, mas você precisa ativar o serviço "
  Automação de envio — Claude" manualmente.

## Como funciona

1. **`AlarmManager`** dispara um alarme exato no horário configurado.
2. O `AlarmReceiver` grava a mensagem pendente e abre o app do Claude
   (`Intent` com `FLAG_ACTIVITY_NEW_TASK`).
3. O `ClaudeAccessibilityService`, que já está observando eventos de janela do
   app do Claude, detecta que a tela apareceu, localiza o campo de texto,
   injeta a mensagem (`ACTION_SET_TEXT`) e toca no botão de enviar
   (`ACTION_CLICK`), com algumas tentativas com espera caso a tela ainda esteja
   carregando.
4. Um **`WorkManager`** periódico (a cada 15 min) reagenda todos os alarmes
   ativos como rede de segurança — alarmes exatos podem ser silenciosamente
   revogados pelo sistema em alguns cenários.
5. Um `BootReceiver` recria os alarmes depois que o aparelho reinicia, já que o
   `AlarmManager` não persiste alarmes entre boots.

Agendamentos podem ser únicos ou repetir em dias específicos da semana.

## Estrutura

```
android/
  app/src/main/java/com/arthurfontana/claudescheduler/
    accessibility/ClaudeAccessibilityService.kt   # injeta texto e clica em enviar
    alarm/        AlarmScheduler.kt, AlarmReceiver.kt, BootReceiver.kt, RescheduleWorker.kt
    data/         ScheduleRepository.kt, PendingMessageStore.kt
    model/        ScheduledMessage.kt
    ui/           MainActivity.kt, AddEditScheduleActivity.kt, ScheduleAdapter.kt
    util/         Constants.kt
```

Sem Room, sem Compose, sem DI — SharedPreferences + JSON e Views XML, de
propósito, para manter a build simples e reprodutível em CI.

## Build local

Requer Android SDK instalado (`ANDROID_HOME`/`ANDROID_SDK_ROOT`) e JDK 17.

```bash
cd android
./gradlew assembleDebug
```

O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

## Build sem instalar nada (GitHub Actions)

Todo push que toque em `android/**` dispara o workflow
`.github/workflows/android-build.yml`, que compila o `.apk` de debug e o
publica como artefato do run. Para baixar pelo celular:

1. Abra o repositório no GitHub (app ou navegador).
2. Aba **Actions** → selecione o run mais recente de "Android Build".
3. Baixe o artefato `claude-scheduler-debug-apk` (é um `.zip` contendo o
   `.apk` — extraia e instale; será preciso permitir instalação de fontes
   desconhecidas).

Este é um app de debug (`applicationId` fixo, sem assinatura de release) —
suficiente para uso pessoal/estudo.
