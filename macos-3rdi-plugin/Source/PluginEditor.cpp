#include "PluginEditor.h"

namespace
{
const auto sand      = juce::Colour::fromRGB (218, 198, 165);
const auto sandLight = juce::Colour::fromRGB (229, 213, 188);
const auto sandDark  = juce::Colour::fromRGB (188, 164, 128);
const auto orange    = juce::Colour::fromRGB (224, 111, 36);
const auto orangeDark= juce::Colour::fromRGB (187, 79, 22);
const auto black     = juce::Colour::fromRGB (18, 18, 16);
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::AnalogLookAndFeel::drawRotarySlider
    (juce::Graphics& g, int x, int y, int width, int height, float sliderPos,
     float rotaryStartAngle, float rotaryEndAngle, juce::Slider&)
{
    auto diameter = (float) juce::jmin (width, height) - 8.0f;
    auto radius = diameter * 0.5f;
    auto centreX = (float) x + (float) width * 0.5f;
    auto centreY = (float) y + (float) height * 0.5f;
    auto r = juce::Rectangle<float> (centreX - radius, centreY - radius, diameter, diameter);

    g.setColour (orange);
    g.fillEllipse (r);
    g.setColour (orangeDark);
    g.fillEllipse (r.reduced (radius * 0.31f));

    auto angle = rotaryStartAngle + sliderPos * (rotaryEndAngle - rotaryStartAngle);
    juce::Path p;
    auto pointerLength = radius * 0.56f;
    auto pointerThickness = juce::jmax (2.0f, radius * 0.09f);
    p.addRoundedRectangle (-pointerThickness * 0.5f, -radius * 0.12f,
                           pointerThickness, pointerLength, pointerThickness * 0.5f);
    p.applyTransform (juce::AffineTransform::rotation (angle)
                                      .translated (centreX, centreY));
    g.setColour (black);
    g.fillPath (p);
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::AnalogLookAndFeel::drawButtonBackground
    (juce::Graphics& g, juce::Button& b, const juce::Colour&, bool over, bool down)
{
    auto r = b.getLocalBounds().toFloat().reduced (1.0f);
    auto fill = b.getToggleState() ? orange : sand;
    if (over) fill = fill.brighter (0.05f);
    if (down) fill = fill.darker (0.08f);
    g.setColour (fill);
    g.fillRoundedRectangle (r, 8.0f);
    g.setColour (orange);
    g.drawRoundedRectangle (r, 8.0f, 2.0f);
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::AnalogLookAndFeel::drawLinearSlider
    (juce::Graphics& g, int x, int y, int width, int height, float sliderPos,
     float, float, juce::Slider::SliderStyle style, juce::Slider& slider)
{
    auto r = juce::Rectangle<float> ((float) x, (float) y, (float) width, (float) height).reduced (1.0f);

    if (slider.getName() == "TEMPO")
    {
        g.setColour (sand);
        g.fillRoundedRectangle (r, 8.0f);
        g.setColour (orange);
        g.drawRoundedRectangle (r, 8.0f, 2.5f);
        g.setColour (black);
        g.setFont (juce::FontOptions (26.0f, juce::Font::bold));
        g.drawFittedText (juce::String (slider.getValue(), 1), r.toNearestInt(),
                          juce::Justification::centred, 1);
        return;
    }

    if (style == juce::Slider::LinearHorizontal)
    {
        auto bar = r.withHeight (19.0f).withCentre ({ r.getCentreX(), r.getCentreY() });
        g.setColour (sandDark);
        g.fillRoundedRectangle (bar, 9.0f);
        auto filled = bar.withRight (sliderPos);
        g.setColour (orange);
        g.fillRoundedRectangle (filled, 9.0f);
        return;
    }

    juce::LookAndFeel_V4::drawLinearSlider (g, x, y, width, height, sliderPos, 0, 0, style, slider);
}

ThreeRDIAnalogPercussionAudioProcessorEditor::ThreeRDIAnalogPercussionAudioProcessorEditor
    (ThreeRDIAnalogPercussionAudioProcessor& p)
    : AudioProcessorEditor (&p), processor (p)
{
    setLookAndFeel (&look);
    setOpaque (true);
    setResizable (false, false);
    setSize (1200, 720);

    auto top = std::array<int, 8> { 220, 345, 470, 595, 720, 845, 970, 1095 };
    addKnob ("vco1",      "VCO 1",      {top[0]-40, 126, 80, 92}, " Hz", 1);
    addKnob ("detune",    "VCO 2",      {top[1]-40, 126, 80, 92}, " st", 1);
    addKnob ("fm",        "FM",         {top[2]-40, 126, 80, 92}, "", 2);
    addKnob ("noise",     "NOISE",      {top[3]-40, 126, 80, 92}, "", 2);
    addKnob ("cutoff",    "CUTOFF",     {top[4]-40, 126, 80, 92}, " Hz", 0);
    addKnob ("resonance", "RESONANCE",  {top[5]-40, 126, 80, 92}, "", 2);
    addKnob ("vcfDecay",  "VCF DECAY",  {top[6]-40, 126, 80, 92}, " ms", 0);
    addKnob ("vcaDecay",  "VCA DECAY",  {top[7]-40, 126, 80, 92}, " ms", 0);

    for (int i = 0; i < 8; ++i)
    {
        int x = 112 + i * 139;
        addKnob ("stepPitch" + juce::String (i + 1), "PITCH",
                 {x - 30, 342, 60, 76}, " st", 1, true);
        addKnob ("stepVel" + juce::String (i + 1), "VELOCITY",
                 {x - 30, 444, 60, 76}, "", 2, true);
    }

    auto mod = std::array<int, 6> {410, 485, 560, 635, 710, 785};
    addKnob ("lfoRate",   "LFO RATE",  {mod[0]-32, 574, 64, 74}, " Hz", 2, true);
    addKnob ("lfoPitch",  "LFO>PITCH", {mod[1]-32, 574, 64, 74}, " st", 1, true);
    addKnob ("lfoFilter", "LFO>VCF",   {mod[2]-32, 574, 64, 74}, " oct", 1, true);
    addKnob ("lfoFm",     "LFO>FM",    {mod[3]-32, 574, 64, 74}, "", 2, true);
    addKnob ("filterEnv", "VCF ENV",   {mod[4]-32, 574, 64, 74}, "x", 1, true);
    addKnob ("pitchEnv",  "PITCH ENV", {mod[5]-32, 574, 64, 74}, " st", 1, true);

    tempoSlider.setName ("TEMPO");
    tempoSlider.setSliderStyle (juce::Slider::LinearBar);
    tempoSlider.setTextBoxStyle (juce::Slider::NoTextBox, false, 0, 0);
    tempoSlider.setBounds (58, 143, 102, 56);
    addAndMakeVisible (tempoSlider);
    tempoAttachment = std::make_unique<SliderAttachment> (processor.apvts, "tempo", tempoSlider);

    driveSlider.setSliderStyle (juce::Slider::LinearHorizontal);
    driveSlider.setTextBoxStyle (juce::Slider::TextBoxRight, false, 48, 20);
    driveSlider.setBounds (68, 598, 290, 34);
    driveSlider.setColour (juce::Slider::textBoxTextColourId, black);
    driveSlider.setColour (juce::Slider::textBoxOutlineColourId, juce::Colours::transparentBlack);
    addAndMakeVisible (driveSlider);
    driveAttachment = std::make_unique<SliderAttachment> (processor.apvts, "drive", driveSlider);

    prefsButton.setClickingTogglesState (true);
    prefsButton.setBounds (1012, 58, 128, 43);
    prefsButton.setColour (juce::TextButton::textColourOffId, black);
    prefsButton.setColour (juce::TextButton::textColourOnId, black);
    addAndMakeVisible (prefsButton);
    hostSyncAttachment = std::make_unique<ButtonAttachment> (processor.apvts, "hostSync", prefsButton);

    runButton.setClickingTogglesState (true);
    runButton.setBounds (890, 618, 95, 53);
    runButton.setColour (juce::TextButton::textColourOffId, black);
    runButton.setColour (juce::TextButton::textColourOnId, black);
    addAndMakeVisible (runButton);
    runAttachment = std::make_unique<ButtonAttachment> (processor.apvts, "run", runButton);

    trigButton.setBounds (995, 618, 85, 53);
    trigButton.setColour (juce::TextButton::textColourOffId, black);
    trigButton.onClick = [this] { processor.manualTrigger(); };
    addAndMakeVisible (trigButton);

    randButton.setBounds (1090, 618, 82, 53);
    randButton.setColour (juce::TextButton::textColourOffId, black);
    randButton.onClick = [this]
    {
        processor.randomizeSequence();
        repaint();
    };
    addAndMakeVisible (randButton);

    startTimerHz (30);
}

ThreeRDIAnalogPercussionAudioProcessorEditor::~ThreeRDIAnalogPercussionAudioProcessorEditor()
{
    stopTimer();
    setLookAndFeel (nullptr);
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::addKnob
    (const juce::String& paramId, const juce::String& labelText,
     juce::Rectangle<int> bounds, const juce::String& suffix, int decimals, bool small)
{
    auto s = std::make_unique<juce::Slider>();
    s->setSliderStyle (juce::Slider::RotaryHorizontalVerticalDrag);
    s->setTextBoxStyle (juce::Slider::TextBoxBelow, false, bounds.getWidth() + 18, small ? 16 : 18);
    s->setTextValueSuffix (suffix);
    s->setNumDecimalPlacesToDisplay (decimals);
    s->setColour (juce::Slider::textBoxTextColourId, black);
    s->setColour (juce::Slider::textBoxBackgroundColourId, juce::Colours::transparentBlack);
    s->setColour (juce::Slider::textBoxOutlineColourId, juce::Colours::transparentBlack);
    s->setBounds (bounds);

    auto label = std::make_unique<juce::Label>();
    label->setText (labelText, juce::dontSendNotification);
    label->setJustificationType (juce::Justification::centred);
    label->setFont (juce::FontOptions (small ? 9.5f : 11.5f, juce::Font::bold));
    label->setColour (juce::Label::textColourId, black);
    label->setBounds (bounds.getX() - 8, bounds.getY() - 20, bounds.getWidth() + 16, 18);

    auto attachment = std::make_unique<SliderAttachment> (processor.apvts, paramId, *s);

    addAndMakeVisible (*s);
    addAndMakeVisible (*label);
    knobAttachments.push_back (std::move (attachment));
    knobLabels.push_back (std::move (label));
    knobs.push_back (std::move (s));
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::paint (juce::Graphics& g)
{
    g.fillAll (sandDark);

    g.setColour (sand);
    g.fillRoundedRectangle ({14.0f, 14.0f, 1172.0f, 692.0f}, 22.0f);
    g.setColour (sandLight);
    g.fillRoundedRectangle ({28.0f, 28.0f, 1144.0f, 664.0f}, 17.0f);

    g.setColour (black);
    g.setFont (juce::FontOptions (31.0f, juce::Font::bold));
    g.drawText ("3RDI ANALOG PERCUSSION", 67, 58, 700, 38, juce::Justification::centredLeft);

    g.setFont (juce::FontOptions (11.5f, juce::Font::bold));
    g.drawText ("DUAL OSCILLATOR / FILTER / MODULATION / 8-STEP PERCUSSION SYNTH",
                68, 98, 760, 18, juce::Justification::centredLeft);

    g.setFont (juce::FontOptions (9.5f));
    g.drawText ("ABLETON HOST SYNC", 1012, 105, 128, 16, juce::Justification::centred);

    g.setFont (juce::FontOptions (11.5f, juce::Font::bold));
    g.drawText ("TEMPO", 58, 205, 102, 18, juce::Justification::centred);
    g.setFont (juce::FontOptions (9.0f));
    g.drawText ("DRAG / TYPE", 58, 224, 102, 15, juce::Justification::centred);

    g.setColour (black.withAlpha (0.28f));
    g.fillRect (62, 251, 1076, 2);

    const int active = processor.getCurrentStep();
    for (int i = 0; i < 8; ++i)
    {
        int x = 112 + i * 139;
        g.setColour (i == active ? orange : black);
        if (i == active)
            g.fillEllipse ((float) x - 9.0f, 277.0f, 18.0f, 18.0f);
        else
            g.drawEllipse ((float) x - 7.0f, 279.0f, 14.0f, 14.0f, 2.0f);

        g.setColour (black);
        g.setFont (juce::FontOptions (13.0f, juce::Font::bold));
        g.drawText (juce::String (i + 1), x - 20, 304, 40, 20, juce::Justification::centred);
    }

    g.setColour (black.withAlpha (0.25f));
    g.fillRect (62, 548, 1076, 2);

    g.setColour (black);
    g.setFont (juce::FontOptions (11.5f, juce::Font::bold));
    g.drawText ("DRIVE", 68, 572, 100, 18, juce::Justification::centredLeft);

    g.setFont (juce::FontOptions (9.0f));
    g.drawText ("3RDI AUDIO LABS • INTEL macOS VST3 / AU",
                880, 682, 292, 16, juce::Justification::centredRight);
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::resized()
{
    // Fixed 1200 x 720 layout mirrors the Android synth panel.
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::timerCallback()
{
    auto run = processor.apvts.getRawParameterValue ("run")->load() > 0.5f;
    runButton.setButtonText (run ? "STOP" : "RUN");
    repaint (80, 270, 1090, 60);
}
