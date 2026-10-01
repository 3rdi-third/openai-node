#include "PluginProcessor.h"
#include "PluginEditor.h"
#include <cmath>

namespace
{
float pval (juce::AudioProcessorValueTreeState& s, const char* id)
{
    return s.getRawParameterValue (id)->load();
}

float morphWave (double phase, float shape)
{
    const float p = (float) phase;
    const float sine = std::sin ((float) (phase * juce::MathConstants<double>::twoPi));
    const float triangle = 1.0f - 4.0f * std::abs (p - 0.5f);
    const float saw = p * 2.0f - 1.0f;

    shape = juce::jlimit (0.0f, 1.0f, shape);
    if (shape < 0.5f)
        return juce::jmap (shape * 2.0f, sine, triangle);

    return juce::jmap ((shape - 0.5f) * 2.0f, triangle, saw);
}

float foldSample (float x)
{
    float y = std::fmod (x + 1.0f, 4.0f);
    if (y < 0.0f)
        y += 4.0f;
    return y < 2.0f ? y - 1.0f : 3.0f - y;
}
}

ThreeRDIAnalogPercussionAudioProcessor::ThreeRDIAnalogPercussionAudioProcessor()
    : AudioProcessor (BusesProperties().withOutput ("Output", juce::AudioChannelSet::stereo(), true)),
      apvts (*this, nullptr, "PARAMETERS", createParameterLayout())
{
}

void ThreeRDIAnalogPercussionAudioProcessor::prepareToPlay (double sampleRate, int)
{
    sr = sampleRate;
    phase1 = phase2 = phaseSub = phaseBody = 0.0;
    lfoPhase = driftPhase1 = driftPhase2 = 0.0;
    samplesToNextStep = 0.0;
    envAmp = envFilter = bodyEnv = 0.0f;
    z1 = z2 = z3 = z4 = 0.0f;
    svfLow = svfBand = 0.0f;
    warmL = warmR = 0.0f;
    step = 0;
    currentStep.store (-1);
    lastHostStep = -1;
}

bool ThreeRDIAnalogPercussionAudioProcessor::isBusesLayoutSupported (const BusesLayout& layouts) const
{
    auto out = layouts.getMainOutputChannelSet();
    return out == juce::AudioChannelSet::mono() || out == juce::AudioChannelSet::stereo();
}

float ThreeRDIAnalogPercussionAudioProcessor::envCoeff (float ms) const noexcept
{
    return std::exp (-1.0f / (0.001f * juce::jmax (2.0f, ms) * (float) sr));
}

float ThreeRDIAnalogPercussionAudioProcessor::getHostTempoAndState (bool& hostPlaying, double& ppq)
{
    hostPlaying = false;
    ppq = 0.0;
    float bpm = pval (apvts, "tempo");

    if (auto* playHead = getPlayHead())
    {
        if (auto pos = playHead->getPosition())
        {
            if (auto b = pos->getBpm())
                bpm = (float) *b;
            hostPlaying = pos->getIsPlaying();
            if (auto p = pos->getPpqPosition())
                ppq = *p;
        }
    }
    return juce::jlimit (40.0f, 300.0f, bpm);
}

void ThreeRDIAnalogPercussionAudioProcessor::triggerStep (int stepIndex, float noteHz)
{
    const auto pitchId = "stepPitch" + juce::String (stepIndex + 1);
    const auto velId   = "stepVel" + juce::String (stepIndex + 1);

    float root = pval (apvts, "vco1");
    float semis = pval (apvts, pitchId.toRawUTF8());
    baseHz = noteHz > 0.0f ? noteHz : root * std::pow (2.0f, semis / 12.0f);
    velocity = pval (apvts, velId.toRawUTF8());
    envAmp = 1.0f;
    envFilter = 1.0f;
    bodyEnv = 1.0f;
}

void ThreeRDIAnalogPercussionAudioProcessor::manualTrigger()
{
    manualTriggerRequested.store (true);
}

void ThreeRDIAnalogPercussionAudioProcessor::randomizeSequence()
{
    static const float scale[] = {-12.0f, -7.0f, -5.0f, 0.0f, 2.0f, 5.0f, 7.0f, 10.0f, 12.0f};

    for (int i = 0; i < 8; ++i)
    {
        const auto pitchId = "stepPitch" + juce::String (i + 1);
        const auto velId   = "stepVel" + juce::String (i + 1);

        if (auto* pitchParam = apvts.getParameter (pitchId))
        {
            const float value = scale[rng.nextInt ((int) std::size (scale))];
            pitchParam->beginChangeGesture();
            pitchParam->setValueNotifyingHost (pitchParam->convertTo0to1 (value));
            pitchParam->endChangeGesture();
        }

        if (auto* velParam = apvts.getParameter (velId))
        {
            const float value = 0.35f + rng.nextFloat() * 0.65f;
            velParam->beginChangeGesture();
            velParam->setValueNotifyingHost (velParam->convertTo0to1 (value));
            velParam->endChangeGesture();
        }
    }
}

void ThreeRDIAnalogPercussionAudioProcessor::processBlock (juce::AudioBuffer<float>& buffer,
                                                            juce::MidiBuffer& midi)
{
    juce::ScopedNoDenormals noDenormals;
    buffer.clear();

    for (const auto metadata : midi)
    {
        auto m = metadata.getMessage();
        if (m.isNoteOn())
        {
            auto hz = (float) juce::MidiMessage::getMidiNoteInHertz (m.getNoteNumber());
            triggerStep (step & 7, hz);
        }
    }

    if (manualTriggerRequested.exchange (false))
        triggerStep (currentStep.load() >= 0 ? currentStep.load() : 0);

    const bool hostSync = pval (apvts, "hostSync") > 0.5f;
    bool hostPlaying = false;
    double ppq = 0.0;
    float bpm = getHostTempoAndState (hostPlaying, ppq);

    const float ampDecay = envCoeff (pval (apvts, "vcaDecay"));
    const float filtDecay = envCoeff (pval (apvts, "vcfDecay"));
    const bool internalRun = pval (apvts, "run") > 0.5f;
    const bool shouldRun = hostSync ? hostPlaying : internalRun;

    if (hostSync && hostPlaying)
    {
        int hostStep = ((int) std::floor (ppq * 2.0)) & 7; // eighth notes
        if (hostStep != lastHostStep)
        {
            lastHostStep = hostStep;
            step = hostStep;
            currentStep.store (step);
            triggerStep (step);
        }
    }

    for (int n = 0; n < buffer.getNumSamples(); ++n)
    {
        if (!hostSync && shouldRun)
        {
            if (samplesToNextStep <= 0.0)
            {
                currentStep.store (step);
                triggerStep (step);
                step = (step + 1) & 7;
                samplesToNextStep += sr * 60.0 / bpm / 2.0;
            }
            samplesToNextStep -= 1.0;
        }

        float lfoRate = pval (apvts, "lfoRate");
        lfoPhase += lfoRate / sr;
        lfoPhase -= std::floor (lfoPhase);
        float lfo = std::sin ((float) (lfoPhase * juce::MathConstants<double>::twoPi));

        float sample = 0.0f;
        if (envAmp > 0.00002f)
        {
            float pitchMod = lfo * pval (apvts, "lfoPitch")
                           + envFilter * pval (apvts, "pitchEnv");

            const float driftAmount = pval (apvts, "drift");
            driftPhase1 += 0.113 / sr;
            driftPhase2 += 0.071 / sr;
            driftPhase1 -= std::floor (driftPhase1);
            driftPhase2 -= std::floor (driftPhase2);

            const float driftCents1 = std::sin ((float) (driftPhase1 * juce::MathConstants<double>::twoPi))
                                    * driftAmount * 9.0f;
            const float driftCents2 = std::sin ((float) (driftPhase2 * juce::MathConstants<double>::twoPi))
                                    * driftAmount * 11.0f;

            float f1 = baseHz * std::pow (2.0f, (pitchMod + driftCents1 * 0.01f) / 12.0f);
            float f2 = baseHz * std::pow (2.0f, (pitchMod
                                             + pval (apvts, "detune")
                                             + driftCents2 * 0.01f) / 12.0f);

            const float shape1 = pval (apvts, "shape1");
            const float shape2 = pval (apvts, "shape2");
            const float crossMod = pval (apvts, "crossMod");

            float osc1Pre = morphWave (phase1, shape1);
            float osc2Pre = morphWave (phase2, shape2);

            float fmDepth = pval (apvts, "fm")
                          * (1.0f + lfo * pval (apvts, "lfoFm"));

            phase1 += (f1 * (1.0 + fmDepth * osc2Pre * 0.34f)) / sr;
            phase2 += (f2 * (1.0 + crossMod * osc1Pre * 0.20f)) / sr;
            phase1 -= std::floor (phase1);
            phase2 -= std::floor (phase2);

            float osc1 = morphWave (phase1, shape1);
            float osc2 = morphWave (phase2, shape2);

            const float subLevel = pval (apvts, "subLevel");
            const float subFrequency = juce::jmax (10.0f, f1 * 0.5f);
            phaseSub += subFrequency / sr;
            phaseSub -= std::floor (phaseSub);
            float sub = std::sin ((float) (phaseSub * juce::MathConstants<double>::twoPi));

            float noise = rng.nextFloat() * 2.0f - 1.0f;
            float baseMix = 0.56f * osc1 + 0.30f * osc2
                          + 0.52f * subLevel * sub
                          + noise * pval (apvts, "noise");

            const float ringAmount = pval (apvts, "ringMod");
            float ring = osc1 * osc2;
            sample = baseMix * (1.0f - ringAmount * 0.42f)
                   + ring * ringAmount * 0.72f;

            const float foldAmount = pval (apvts, "wavefold");
            if (foldAmount > 0.0001f)
            {
                float folded = foldSample (sample * (1.0f + foldAmount * 5.5f));
                sample = juce::jmap (foldAmount, sample, folded);
            }

            const float bodyLevel = pval (apvts, "bodyLevel");
            const float bodyTune = pval (apvts, "bodyTune");
            const float bodyHz = juce::jlimit (16.0f, 500.0f,
                baseHz * std::pow (2.0f, bodyTune / 12.0f));
            phaseBody += bodyHz / sr;
            phaseBody -= std::floor (phaseBody);
            float bodyOsc = std::sin ((float) (phaseBody * juce::MathConstants<double>::twoPi));
            sample += bodyOsc * bodyEnv * bodyLevel * 0.72f;

            float cutoff = pval (apvts, "cutoff")
                         * std::pow (2.0f, lfo * pval (apvts, "lfoFilter"))
                         * (1.0f + envFilter * pval (apvts, "filterEnv"));
            cutoff = juce::jlimit (30.0f, (float) sr * 0.42f, cutoff);

            const float filterDrive = pval (apvts, "filterDrive");
            float filterInput = std::tanh (sample * (1.0f + filterDrive * 6.0f));

            float g = 1.0f - std::exp (-2.0f * juce::MathConstants<float>::pi * cutoff / (float) sr);
            float res = juce::jlimit (0.0f, 0.96f, pval (apvts, "resonance"));
            float x = filterInput - z4 * res * 3.4f;
            z1 += g * (std::tanh (x) - z1);
            z2 += g * (z1 - z2);
            z3 += g * (z2 - z3);
            z4 += g * (z3 - z4);

            const float svfCut = juce::jmin (cutoff, (float) sr * 0.24f);
            const float svfF = juce::jlimit (0.001f, 0.99f,
                2.0f * std::sin (juce::MathConstants<float>::pi * svfCut / (float) sr));
            const float damping = juce::jlimit (0.08f, 1.0f, 1.0f - res * 0.86f);
            svfLow += svfF * svfBand;
            float svfHigh = filterInput - svfLow - damping * svfBand;
            svfBand += svfF * svfHigh;

            const float morph = pval (apvts, "filterMorph");
            if (morph < 0.5f)
                sample = juce::jmap (morph * 2.0f, z4, svfLow);
            else
                sample = juce::jmap ((morph - 0.5f) * 2.0f, svfLow, svfBand);

            const float drive = pval (apvts, "drive");
            const float warmth = pval (apvts, "warmth");
            sample = std::tanh (sample * drive * (1.0f + warmth * 0.85f))
                   * envAmp * velocity;

            float side = 0.0f;
            const float width = pval (apvts, "stereoWidth");
            if (buffer.getNumChannels() > 1)
                side = (osc2 - osc1 + 0.35f * sub) * width * envAmp * velocity * 0.12f;

            const float warmCut = 18000.0f - warmth * 10000.0f;
            const float warmAlpha = 1.0f - std::exp (-2.0f * juce::MathConstants<float>::pi
                                                   * warmCut / (float) sr);
            float left = sample + side;
            float right = sample - side;
            warmL += warmAlpha * (left - warmL);
            warmR += warmAlpha * (right - warmR);

            left = std::tanh (warmL * (1.0f + warmth * 0.9f));
            right = std::tanh (warmR * (1.0f + warmth * 0.9f));

            if (buffer.getNumChannels() > 0)
                buffer.setSample (0, n, left * 0.52f);
            if (buffer.getNumChannels() > 1)
                buffer.setSample (1, n, right * 0.52f);

            envAmp *= ampDecay;
            envFilter *= filtDecay;
            bodyEnv *= envCoeff (pval (apvts, "bodyDecay"));
        }

        if (envAmp <= 0.00002f)
        {
            warmL *= 0.995f;
            warmR *= 0.995f;
            if (buffer.getNumChannels() > 0)
                buffer.setSample (0, n, warmL * 0.52f);
            if (buffer.getNumChannels() > 1)
                buffer.setSample (1, n, warmR * 0.52f);
        }
    }
}

juce::AudioProcessorValueTreeState::ParameterLayout
ThreeRDIAnalogPercussionAudioProcessor::createParameterLayout()
{
    std::vector<std::unique_ptr<juce::RangedAudioParameter>> p;
    auto f = [&p](const char* id, const char* name, float lo, float hi, float def, float skew = 1.0f)
    {
        juce::NormalisableRange<float> r (lo, hi);
        r.setSkewForCentre (skew > lo && skew < hi ? skew : (lo + hi) * 0.5f);
        p.push_back (std::make_unique<juce::AudioParameterFloat> (juce::ParameterID{id, 1}, name, r, def));
    };

    f ("vco1", "VCO 1 Hz", 28, 440, 62, 100);
    f ("detune", "VCO 2 Detune", -24, 24, 7);
    f ("fm", "FM", 0, 1, 0.28f);
    f ("noise", "Noise", 0, 1, 0.12f);
    f ("cutoff", "Cutoff", 70, 12000, 780, 1200);
    f ("resonance", "Resonance", 0, 0.96f, 0.34f);
    f ("vcfDecay", "VCF Decay ms", 35, 2400, 420, 500);
    f ("vcaDecay", "VCA Decay ms", 35, 2400, 330, 400);
    f ("drive", "Drive", 1, 6, 2.2f);
    f ("lfoRate", "LFO Rate", 0.05f, 30, 3, 3);
    f ("lfoPitch", "LFO to Pitch", -12, 12, 0);
    f ("lfoFilter", "LFO to Filter", -3, 3, 0);
    f ("lfoFm", "LFO to FM", 0, 1, 0);
    f ("filterEnv", "Filter Env", 0, 10, 5.5f);
    f ("pitchEnv", "Pitch Env", -24, 24, 0);

    f ("shape1", "Osc 1 Shape", 0, 1, 0.18f);
    f ("shape2", "Osc 2 Shape", 0, 1, 0.38f);
    f ("subLevel", "Sub Level", 0, 1, 0.34f);
    f ("ringMod", "Ring Mod", 0, 1, 0.08f);
    f ("crossMod", "Cross Mod", 0, 1, 0.12f);
    f ("wavefold", "Wavefold", 0, 1, 0.06f);
    f ("bodyLevel", "Body Level", 0, 1, 0.32f);
    f ("bodyTune", "Body Tune", -36, 12, -12);
    f ("bodyDecay", "Body Decay ms", 50, 3000, 720, 800);
    f ("filterMorph", "Filter Morph", 0, 1, 0.18f);
    f ("filterDrive", "Filter Drive", 0, 1, 0.24f);
    f ("drift", "Analog Drift", 0, 1, 0.20f);
    f ("warmth", "Warmth", 0, 1, 0.48f);
    f ("stereoWidth", "Stereo Width", 0, 1, 0.22f);

    f ("tempo", "Internal Tempo", 40, 300, 133, 133);
    p.push_back (std::make_unique<juce::AudioParameterBool> (juce::ParameterID{"hostSync", 1}, "Ableton Host Sync", true));
    p.push_back (std::make_unique<juce::AudioParameterBool> (juce::ParameterID{"run", 1}, "Run Sequencer", true));

    const float pitchDefaults[8] = {0,0,7,-5,0,12,-2,5};
    const float velDefaults[8] = {1.0f,0.58f,0.82f,0.52f,0.96f,0.72f,0.45f,0.88f};
    for (int i = 0; i < 8; ++i)
    {
        auto pi = "stepPitch" + juce::String(i + 1);
        auto vi = "stepVel" + juce::String(i + 1);
        p.push_back (std::make_unique<juce::AudioParameterFloat>
            (juce::ParameterID{pi, 1}, "Step " + juce::String(i + 1) + " Pitch",
             juce::NormalisableRange<float>(-24,24), pitchDefaults[i]));
        p.push_back (std::make_unique<juce::AudioParameterFloat>
            (juce::ParameterID{vi, 1}, "Step " + juce::String(i + 1) + " Velocity",
             juce::NormalisableRange<float>(0,1), velDefaults[i]));
    }
    return { p.begin(), p.end() };
}

void ThreeRDIAnalogPercussionAudioProcessor::getStateInformation (juce::MemoryBlock& destData)
{
    if (auto xml = apvts.copyState().createXml())
        copyXmlToBinary (*xml, destData);
}

void ThreeRDIAnalogPercussionAudioProcessor::setStateInformation (const void* data, int size)
{
    if (auto xml = getXmlFromBinary (data, size))
        apvts.replaceState (juce::ValueTree::fromXml (*xml));
}

juce::AudioProcessorEditor* ThreeRDIAnalogPercussionAudioProcessor::createEditor()
{
    return new ThreeRDIAnalogPercussionAudioProcessorEditor (*this);
}

juce::AudioProcessor* JUCE_CALLTYPE createPluginFilter()
{
    return new ThreeRDIAnalogPercussionAudioProcessor();
}
