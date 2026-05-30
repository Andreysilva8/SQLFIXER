package br.com.empresa.intranet.service;

import br.com.empresa.intranet.dto.request.AnalysisSettings;
import org.springframework.stereotype.Service;

@Service
public class AnalysisSettingsService {
    private final AnalysisSettings settings = AnalysisSettings.defaults();

    public synchronized AnalysisSettings getSettings() {
        return copy(settings);
    }

    public synchronized AnalysisSettings update(AnalysisSettings newSettings) {
        settings.merge(newSettings);
        return copy(settings);
    }

    public AnalysisSettings resolve(AnalysisSettings requestSettings) {
        AnalysisSettings resolved = getSettings();
        if (requestSettings != null) {
            resolved.merge(requestSettings);
        }
        return resolved;
    }

    private AnalysisSettings copy(AnalysisSettings source) {
        AnalysisSettings copy = AnalysisSettings.defaults();
        copy.merge(source);
        return copy;
    }
}
