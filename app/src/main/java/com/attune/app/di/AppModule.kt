package com.attune.app.di
import android.content.Context
import com.attune.core.data.db.AttuneDatabase
import com.attune.prediction.AttentionPredictor
import com.attune.prediction.PredictionEngine
import com.attune.prediction.PriorLoader
import dagger.Module; import dagger.Provides; import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton fun provideDatabase(@ApplicationContext ctx:Context) = AttuneDatabase.getInstance(ctx)
    @Provides fun provideUserProfileDao(db:AttuneDatabase) = db.userProfileDao()
    @Provides fun provideFocusSessionDao(db:AttuneDatabase) = db.focusSessionDao()
    @Provides fun provideQueuedNotificationDao(db:AttuneDatabase) = db.queuedNotificationDao()
    @Provides fun providePredictionDao(db:AttuneDatabase) = db.predictionDao()
    @Provides fun provideUserActionDao(db:AttuneDatabase) = db.userActionDao()
    @Provides fun provideFeedbackDao(db:AttuneDatabase) = db.feedbackDao()
    @Provides fun provideAttentionProfileDao(db:AttuneDatabase) = db.attentionProfileDao()
    @Provides fun provideEnergyCheckInDao(db:AttuneDatabase) = db.energyCheckInDao()
    @Provides fun provideDecisionDao(db:AttuneDatabase) = db.decisionDao()
    @Provides fun provideFeedbackEventDao(db:AttuneDatabase) = db.feedbackEventDao()
    @Provides fun provideUserRuleDao(db:AttuneDatabase) = db.userRuleDao()
    @Provides fun provideSessionEventDao(db:AttuneDatabase) = db.sessionEventDao()
}
@Module @InstallIn(SingletonComponent::class)
object PredictionModule {
    /** Swap this binding to plug in a trained model that implements [AttentionPredictor]. */
    @Provides @Singleton fun providePredictor(@ApplicationContext ctx:Context): AttentionPredictor {
        val json=runCatching { ctx.assets.open("priors.json").bufferedReader(Charsets.UTF_8).readText() }.getOrDefault("{}")
        return PredictionEngine(PriorLoader.loadFromJson(json), k=5)
    }
}
