#include "PluginProcessor.h"
#include "PluginEditor.h"
#include <cmath>

namespace
{
float pval (juce::AudioProcessorValueTreeState& s, const char* id)
{
    return s.getRawParameterValue (id)->load();
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
    phase1 = phase2 = lfoPhase = 0.0;
    samplesToNextStep = 0.0;
    envAmp = envFilter = 0.0f;
    z1 = z2 = z3 = z4 = 0.0f;
    step = 0;
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

    const bool hostSync = pval (apvts, "hostSync") > 0.5f;
    bool hostPlaying = false;
    double ppq = 0.0;
    float bpm = getHostTempoAndState (hostPlaying, ppq);

    const float ampDecay = envCoeff (pval (apvts, "vcaDecay"));
    const float filtDecay = envCoeff (pval (apvts, "vcfDecay"));
    const bool shouldRun = !hostSync || hostPlaying;

    if (hostSync && hostPlaying)
    {
        int hostStep = ((int) std::floor (ppq * 2.0)) & 7; // eighth notes
        if (hostStep != lastHostStep)
        {
            lastHostStep = hostStep;
            step = hostStep;
            triggerStep (step);
        }
    }

    for (int n = 0; n < buffer.getNumSamples(); ++n)
    {
        if (!hostSync && shouldRun)
        {
            if (samplesToNextStep <= 0.0)
            {
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
            float f1 = baseHz * std::pow (2.0f, pitchMod / 12.0f);
            float f2 = f1 * std::pow (2.0f, pval (apvts, "detune") / 12.0f);

            float fmDepth = pval (apvts, "fm")
                          * (1.0f + lfo * pval (apvts, "lfoFm"));
            float osc2 = std::sin ((float) (phase2 * juce::MathConstants<double>::twoPi));
            phase1 += (f1 * (1.0 + fmDepth * osc2 * 0.35f)) / sr;
            phase2 += f2 / sr;
            phase1 -= std::floor (phase1);
            phase2 -= std::floor (phase2);

            float osc1 = std::sin ((float) (phase1 * juce::MathConstants<double>::twoPi));
            float noise = rng.nextFloat() * 2.0f - 1.0f;
            sample = (0.72f * osc1 + 0.28f * osc2 + noise * pval (apvts, "noise"));

            float cutoff = pval (apvts, "cutoff")
                         * std::pow (2.0f, lfo * pval (apvts, "lfoFilter"))
                         * (1.0f + envFilter * pval (apvts, "filterEnv"));
            cutoff = juce::jlimit (30.0f, (float) sr * 0.42f, cutoff);

            float g = 1.0f - std::exp (-2.0f * juce::MathConstants<float>::pi * cutoff / (float) sr);
            float res = juce::jlimit (0.0f, 0.96f, pval (apvts, "resonance"));
            float x = sample - z4 * res * 3.4f;
            z1 += g * (std::tanh (x) - z1);
            z2 += g * (z1 - z2);
            z3 += g * (z2 - z3);
            z4 += g * (z3 - z4);
            sample = z4;

            sample = std::tanh (sample * pval (apvts, "drive")) * envAmp * velocity;
            envAmp *= ampDecay;
            envFilter *= filtDecay;
        }

        for (int ch = 0; ch < buffer.getNumChannels(); ++ch)
            buffer.setSample (ch, n, sample * 0.55f);
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
    f ("tempo", "Internal Tempo", 40, 300, 133, 133);
    p.push_back (std::make_unique<juce::AudioParameterBool> (juce::ParameterID{"hostSync", 1}, "Ableton Host Sync", true));

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
