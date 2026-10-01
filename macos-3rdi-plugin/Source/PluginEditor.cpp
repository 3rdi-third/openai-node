#include "PluginEditor.h"

namespace
{
const auto defaultPanel  = juce::Colour::fromRGB (218, 198, 165);
const auto defaultKnob   = juce::Colour::fromRGB (224, 111, 36);
const auto defaultText   = juce::Colour::fromRGB (18, 18, 16);
const auto defaultAccent = juce::Colour::fromRGB (187, 79, 22);

const juce::Identifier panelColourProperty  { "themePanelColour" };
const juce::Identifier knobColourProperty   { "themeKnobColour" };
const juce::Identifier textColourProperty   { "themeTextColour" };
const juce::Identifier accentColourProperty { "themeAccentColour" };
}

ThreeRDIAnalogPercussionAudioProcessorEditor::CallbackColourSelector::CallbackColourSelector
    (juce::Colour initial, std::function<void(juce::Colour)> callbackIn)
    : juce::ColourSelector (juce::ColourSelector::showColourAtTop
                          | juce::ColourSelector::showSliders
                          | juce::ColourSelector::showColourspace),
      callback (std::move (callbackIn))
{
    setCurrentColour (initial);
    setSize (360, 420);
    addChangeListener (this);
}

ThreeRDIAnalogPercussionAudioProcessorEditor::CallbackColourSelector::~CallbackColourSelector()
{
    removeChangeListener (this);
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::CallbackColourSelector::changeListenerCallback
    (juce::ChangeBroadcaster*)
{
    if (callback)
        callback (getCurrentColour());
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::AnalogLookAndFeel::setTheme
    (juce::Colour panel, juce::Colour knob, juce::Colour text, juce::Colour accent)
{
    panelColour = panel;
    knobColour = knob;
    textColour = text;
    accentColour = accent;
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::AnalogLookAndFeel::drawRotarySlider
    (juce::Graphics& g, int x, int y, int width, int height, float sliderPos,
     float rotaryStartAngle, float rotaryEndAngle, juce::Slider&)
{
    auto diameter = (float) juce::jmin (width, height) - 14.0f;
    auto radius = diameter * 0.5f;
    auto cx = (float) x + (float) width * 0.5f;
    auto cy = (float) y + (float) height * 0.44f;
    auto r = juce::Rectangle<float> (cx - radius, cy - radius, diameter, diameter);

    const float startA = -2.35f;
    const float endA = 2.35f;

    g.setColour (textColour.withAlpha (0.42f));
    for (int i = 0; i <= 10; ++i)
    {
        auto a = juce::jmap ((float) i / 10.0f, startA, endA);
        auto inner = radius + 4.0f;
        auto outer = radius + (i % 5 == 0 ? 10.0f : 8.0f);
        juce::Point<float> p1 (cx + std::sin (a) * inner, cy - std::cos (a) * inner);
        juce::Point<float> p2 (cx + std::sin (a) * outer, cy - std::cos (a) * outer);
        g.drawLine ({p1, p2}, i % 5 == 0 ? 1.7f : 1.0f);
    }

    g.setColour (juce::Colours::black.withAlpha (0.22f));
    g.fillEllipse (r.translated (2.5f, 4.0f).expanded (4.0f));

    g.setColour (juce::Colour::fromRGB (28, 27, 24));
    g.fillEllipse (r.expanded (4.0f));
    g.setColour (accentColour.darker (0.48f));
    g.drawEllipse (r.expanded (3.0f), 2.0f);

    juce::ColourGradient face (knobColour.brighter (0.18f), cx - radius * 0.4f, cy - radius * 0.55f,
                               knobColour.darker (0.16f), cx + radius * 0.45f, cy + radius * 0.55f, false);
    g.setGradientFill (face);
    g.fillEllipse (r.reduced (2.5f));

    g.setColour (juce::Colours::black.withAlpha (0.20f));
    g.drawEllipse (r.reduced (3.0f), 1.3f);

    auto angle = rotaryStartAngle + sliderPos * (rotaryEndAngle - rotaryStartAngle);
    juce::Path pointer;
    pointer.addRoundedRectangle (-2.0f, -radius * 0.78f, 4.0f, radius * 0.62f, 2.0f);
    pointer.applyTransform (juce::AffineTransform::rotation (angle).translated (cx, cy));
    g.setColour (juce::Colours::white.withAlpha (0.92f));
    g.fillPath (pointer);

    g.setColour (textColour.withAlpha (0.75f));
    g.fillEllipse (cx - 2.4f, cy - 2.4f, 4.8f, 4.8f);
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::AnalogLookAndFeel::drawButtonBackground
    (juce::Graphics& g, juce::Button& b, const juce::Colour&, bool over, bool down)
{
    auto r = b.getLocalBounds().toFloat().reduced (1.5f);
    const bool pad = b.getName() == "STEP_PAD";
    const bool transport = b.getName() == "TRANSPORT";

    g.setColour (juce::Colours::black.withAlpha (0.18f));
    g.fillRoundedRectangle (r.translated (1.5f, 2.5f), 6.0f);

    juce::Colour fill = panelColour.darker (0.03f);
    if (pad || transport)
        fill = juce::Colour::fromRGB (30, 29, 26);

    if (b.getToggleState())
        fill = pad || transport ? juce::Colour::fromRGB (42, 35, 29) : knobColour.withAlpha (0.78f);

    if (over) fill = fill.brighter (0.07f);
    if (down) fill = fill.darker (0.12f);

    g.setColour (fill);
    g.fillRoundedRectangle (r, 6.0f);
    g.setColour (b.getToggleState() ? knobColour : accentColour.withAlpha (0.88f));
    g.drawRoundedRectangle (r, 6.0f, b.getToggleState() ? 2.6f : 1.5f);

    if (pad || transport)
    {
        g.setColour (juce::Colours::white.withAlpha (0.08f));
        g.drawRoundedRectangle (r.reduced (3.0f), 4.0f, 1.0f);
    }
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::AnalogLookAndFeel::drawLinearSlider
    (juce::Graphics& g, int x, int y, int width, int height, float sliderPos,
     float, float, juce::Slider::SliderStyle style, juce::Slider& slider)
{
    auto r = juce::Rectangle<float> ((float) x, (float) y, (float) width, (float) height).reduced (1.0f);

    if (slider.getName() == "TEMPO")
    {
        g.setColour (juce::Colours::black.withAlpha (0.28f));
        g.fillRoundedRectangle (r.translated (2.0f, 3.0f), 6.0f);

        g.setColour (juce::Colour::fromRGB (17, 17, 15));
        g.fillRoundedRectangle (r, 5.0f);
        g.setColour (accentColour);
        g.drawRoundedRectangle (r, 5.0f, 2.0f);

        g.setColour (knobColour.brighter (0.15f));
        g.setFont (juce::FontOptions (32.0f, juce::Font::bold));
        g.drawFittedText (juce::String (slider.getValue(), 1), r.toNearestInt(),
                          juce::Justification::centred, 1);
        return;
    }

    if (style == juce::Slider::LinearHorizontal)
    {
        auto bar = r.withHeight (18.0f).withCentre ({ r.getCentreX(), r.getCentreY() });
        g.setColour (juce::Colour::fromRGB (32, 30, 28));
        g.fillRoundedRectangle (bar, 5.0f);
        g.setColour (accentColour.withAlpha (0.85f));
        g.drawRoundedRectangle (bar, 5.0f, 1.4f);

        auto filled = bar.withRight (sliderPos);
        g.setColour (knobColour);
        g.fillRoundedRectangle (filled.reduced (3.0f, 5.0f), 3.0f);

        auto thumb = juce::Rectangle<float> (sliderPos - 8.0f, bar.getY() - 8.0f, 16.0f, bar.getHeight() + 16.0f);
        g.setColour (juce::Colour::fromRGB (45, 42, 38));
        g.fillRoundedRectangle (thumb, 3.0f);
        g.setColour (knobColour);
        g.fillRoundedRectangle (thumb.reduced (4.0f, 2.0f), 2.0f);
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
    setSize (1440, 1024);

    if (! processor.apvts.state.hasProperty (panelColourProperty))
        processor.apvts.state.setProperty (panelColourProperty, (int64) defaultPanel.getARGB(), nullptr);
    if (! processor.apvts.state.hasProperty (knobColourProperty))
        processor.apvts.state.setProperty (knobColourProperty, (int64) defaultKnob.getARGB(), nullptr);
    if (! processor.apvts.state.hasProperty (textColourProperty))
        processor.apvts.state.setProperty (textColourProperty, (int64) defaultText.getARGB(), nullptr);
    if (! processor.apvts.state.hasProperty (accentColourProperty))
        processor.apvts.state.setProperty (accentColourProperty, (int64) defaultAccent.getARGB(), nullptr);

    applyThemeFromState (true);

    // SOURCE / FILTER row
    auto top = std::array<int, 8> { 280, 430, 580, 730, 895, 1045, 1195, 1340 };
    addKnob ("vco1",      "VCO 1",      {top[0]-48, 160, 96, 116}, " Hz", 1);
    addKnob ("detune",    "VCO 2",      {top[1]-48, 160, 96, 116}, " st", 1);
    addKnob ("fm",        "FM",         {top[2]-48, 160, 96, 116}, "", 2);
    addKnob ("noise",     "NOISE",      {top[3]-48, 160, 96, 116}, "", 2);
    addKnob ("cutoff",    "CUTOFF",     {top[4]-48, 160, 96, 116}, " Hz", 0);
    addKnob ("resonance", "RESONANCE",  {top[5]-48, 160, 96, 116}, "", 2);
    addKnob ("vcfDecay",  "VCF DECAY",  {top[6]-48, 160, 96, 116}, " ms", 0);
    addKnob ("vcaDecay",  "VCA DECAY",  {top[7]-48, 160, 96, 116}, " ms", 0);

    // SEQUENCER: pad + pitch + velocity per step
    for (int i = 0; i < 8; ++i)
    {
        const int cx = 120 + i * 170;

        auto pad = std::make_unique<juce::TextButton> (juce::String (i + 1));
        pad->setName ("STEP_PAD");
        pad->setBounds (cx - 70, 405, 140, 40);
        pad->setColour (juce::TextButton::textColourOffId, juce::Colours::white);
        pad->setColour (juce::TextButton::textColourOnId, juce::Colours::white);
        pad->onClick = [this, i] { processor.requestTriggerStep (i); };
        addAndMakeVisible (*pad);
        stepPads[(size_t) i] = std::move (pad);

        addKnob ("stepPitch" + juce::String (i + 1), "PITCH",
                 {cx - 72, 470, 64, 92}, " st", 1, true);
        addKnob ("stepVel" + juce::String (i + 1), "VELOCITY",
                 {cx + 8, 470, 64, 92}, "", 2, true);
    }

    // MODULATION
    auto mod = std::array<int, 6> { 105, 225, 345, 465, 585, 705 };
    addKnob ("lfoRate",   "LFO RATE",  {mod[0]-36, 660, 72, 94}, " Hz", 2, true);
    addKnob ("lfoPitch",  "LFO > PITCH",{mod[1]-36, 660, 72, 94}, " st", 1, true);
    addKnob ("lfoFilter", "LFO > VCF", {mod[2]-36, 660, 72, 94}, " oct", 1, true);
    addKnob ("lfoFm",     "LFO > FM",  {mod[3]-36, 660, 72, 94}, "", 2, true);
    addKnob ("filterEnv", "VCF ENV",   {mod[4]-36, 660, 72, 94}, "x", 1, true);
    addKnob ("pitchEnv",  "PITCH ENV", {mod[5]-36, 660, 72, 94}, " st", 1, true);

    // DEEP / STRUCTURE
    auto deep = std::array<int, 14> { 82, 180, 278, 376, 474, 572, 670,
                                      768, 866, 964, 1062, 1160, 1258, 1356 };
    addKnob ("shape1",      "SHAPE 1",   {deep[0]-34, 870, 68, 92}, "", 2, true);
    addKnob ("shape2",      "SHAPE 2",   {deep[1]-34, 870, 68, 92}, "", 2, true);
    addKnob ("subLevel",    "SUB",       {deep[2]-34, 870, 68, 92}, "", 2, true);
    addKnob ("ringMod",     "RING",      {deep[3]-34, 870, 68, 92}, "", 2, true);
    addKnob ("crossMod",    "X-MOD",     {deep[4]-34, 870, 68, 92}, "", 2, true);
    addKnob ("wavefold",    "FOLD",      {deep[5]-34, 870, 68, 92}, "", 2, true);
    addKnob ("bodyLevel",   "BODY",      {deep[6]-34, 870, 68, 92}, "", 2, true);
    addKnob ("bodyTune",    "BODY TUNE", {deep[7]-34, 870, 68, 92}, " st", 0, true);
    addKnob ("bodyDecay",   "BODY DEC",  {deep[8]-34, 870, 68, 92}, " ms", 0, true);
    addKnob ("filterMorph", "FLT MORPH", {deep[9]-34, 870, 68, 92}, "", 2, true);
    addKnob ("filterDrive", "FLT DRIVE", {deep[10]-34,870, 68, 92}, "", 2, true);
    addKnob ("drift",       "DRIFT",     {deep[11]-34,870, 68, 92}, "", 2, true);
    addKnob ("warmth",      "WARMTH",    {deep[12]-34,870, 68, 92}, "", 2, true);
    addKnob ("stereoWidth", "WIDTH",     {deep[13]-34,870, 68, 92}, "", 2, true);

    tempoSlider.setName ("TEMPO");
    tempoSlider.setSliderStyle (juce::Slider::LinearBar);
    tempoSlider.setTextBoxStyle (juce::Slider::NoTextBox, false, 0, 0);
    tempoSlider.setBounds (47, 165, 138, 68);
    addAndMakeVisible (tempoSlider);
    tempoAttachment = std::make_unique<SliderAttachment> (processor.apvts, "tempo", tempoSlider);

    driveSlider.setSliderStyle (juce::Slider::LinearHorizontal);
    driveSlider.setTextBoxStyle (juce::Slider::TextBoxRight, false, 54, 22);
    driveSlider.setBounds (815, 690, 255, 42);
    addAndMakeVisible (driveSlider);
    driveAttachment = std::make_unique<SliderAttachment> (processor.apvts, "drive", driveSlider);

    themeButton.setBounds (870, 30, 118, 50);
    themeButton.onClick = [this] { showThemeMenu(); };
    addAndMakeVisible (themeButton);

    saveButton.setBounds (1000, 30, 108, 50);
    saveButton.onClick = [this] { savePreset(); };
    addAndMakeVisible (saveButton);

    loadButton.setBounds (1120, 30, 108, 50);
    loadButton.onClick = [this] { loadPreset(); };
    addAndMakeVisible (loadButton);

    prefsButton.setClickingTogglesState (true);
    prefsButton.setBounds (1240, 30, 160, 50);
    addAndMakeVisible (prefsButton);
    hostSyncAttachment = std::make_unique<ButtonAttachment> (processor.apvts, "hostSync", prefsButton);

    runButton.setName ("TRANSPORT");
    runButton.setClickingTogglesState (true);
    runButton.setBounds (1105, 665, 90, 78);
    addAndMakeVisible (runButton);
    runAttachment = std::make_unique<ButtonAttachment> (processor.apvts, "run", runButton);

    trigButton.setName ("TRANSPORT");
    trigButton.setBounds (1210, 665, 90, 78);
    trigButton.onClick = [this] { processor.manualTrigger(); };
    addAndMakeVisible (trigButton);

    randButton.setName ("TRANSPORT");
    randButton.setBounds (1315, 665, 90, 78);
    randButton.onClick = [this]
    {
        processor.randomizeSequence();
        repaint();
    };
    addAndMakeVisible (randButton);

    applyThemeFromState (true);
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
    s->setTextBoxStyle (juce::Slider::TextBoxBelow, false,
                        bounds.getWidth() + (small ? 16 : 22), small ? 18 : 21);
    s->setTextValueSuffix (suffix);
    s->setNumDecimalPlacesToDisplay (decimals);
    s->setColour (juce::Slider::textBoxTextColourId, look.textColour);
    s->setColour (juce::Slider::textBoxBackgroundColourId, juce::Colours::transparentBlack);
    s->setColour (juce::Slider::textBoxOutlineColourId, juce::Colours::transparentBlack);
    s->setBounds (bounds);

    auto label = std::make_unique<juce::Label>();
    label->setText (labelText, juce::dontSendNotification);
    label->setJustificationType (juce::Justification::centred);
    label->setFont (juce::FontOptions (small ? 10.5f : 13.0f, juce::Font::bold));
    label->setColour (juce::Label::textColourId, look.textColour);
    label->setBounds (bounds.getX() - 12, bounds.getY() - 23, bounds.getWidth() + 24, 20);

    auto attachment = std::make_unique<SliderAttachment> (processor.apvts, paramId, *s);
    addAndMakeVisible (*s);
    addAndMakeVisible (*label);

    knobAttachments.push_back (std::move (attachment));
    knobLabels.push_back (std::move (label));
    knobs.push_back (std::move (s));
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::paint (juce::Graphics& g)
{
    auto panel = look.panelColour;
    auto text = look.textColour;
    auto accent = look.accentColour;
    auto knob = look.knobColour;

    juce::ColourGradient bg (panel.darker (0.34f), 0.0f, 0.0f,
                             panel.darker (0.16f), 0.0f, (float) getHeight(), false);
    g.setGradientFill (bg);
    g.fillAll();

    auto drawScrew = [&] (float x, float y)
    {
        g.setColour (juce::Colours::black.withAlpha (0.28f));
        g.fillEllipse (x - 7.0f, y - 6.0f, 14.0f, 14.0f);
        juce::ColourGradient sg (panel.brighter (0.18f), x - 4.0f, y - 4.0f,
                                 panel.darker (0.48f), x + 4.0f, y + 4.0f, false);
        g.setGradientFill (sg);
        g.fillEllipse (x - 6.0f, y - 6.0f, 12.0f, 12.0f);
        g.setColour (text.withAlpha (0.72f));
        g.drawLine (x - 3.0f, y, x + 3.0f, y, 1.2f);
    };

    auto drawPanel = [&] (juce::Rectangle<float> r, const juce::String& title)
    {
        g.setColour (juce::Colours::black.withAlpha (0.22f));
        g.fillRoundedRectangle (r.translated (1.5f, 3.0f), 9.0f);

        juce::ColourGradient pg (panel.brighter (0.10f), r.getX(), r.getY(),
                                 panel.darker (0.06f), r.getRight(), r.getBottom(), false);
        g.setGradientFill (pg);
        g.fillRoundedRectangle (r, 9.0f);

        g.setColour (text.withAlpha (0.30f));
        g.drawRoundedRectangle (r, 9.0f, 1.1f);

        if (title.isNotEmpty())
        {
            g.setColour (text);
            g.setFont (juce::FontOptions (18.0f, juce::Font::bold));
            g.drawText (title, (int) r.getX() + 18, (int) r.getY() + 8,
                        340, 25, juce::Justification::centredLeft);
            g.setColour (text.withAlpha (0.35f));
            g.fillRect ((int) r.getX() + 170, (int) r.getY() + 21,
                        (int) r.getWidth() - 194, 1);
        }

        drawScrew (r.getX() + 14.0f, r.getY() + 14.0f);
        drawScrew (r.getRight() - 14.0f, r.getY() + 14.0f);
        drawScrew (r.getX() + 14.0f, r.getBottom() - 14.0f);
        drawScrew (r.getRight() - 14.0f, r.getBottom() - 14.0f);
    };

    // Header
    drawPanel ({8.0f, 8.0f, 1424.0f, 88.0f}, {});
    g.setColour (text);
    g.setFont (juce::FontOptions (48.0f, juce::Font::bold));
    g.drawText ("3RDI", 50, 18, 145, 58, juce::Justification::centredLeft);
    g.setFont (juce::FontOptions (31.0f, juce::Font::bold));
    g.drawText ("ANALOG PERCUSSION", 205, 23, 520, 42, juce::Justification::centredLeft);
    g.setFont (juce::FontOptions (11.0f, juce::Font::bold));
    g.drawText ("DEEP ANALOG PERCUSSION SYNTHESIZER", 208, 63, 430, 18,
                juce::Justification::centredLeft);

    // Main source/filter
    drawPanel ({12.0f, 108.0f, 1416.0f, 212.0f}, {});
    g.setColour (text.withAlpha (0.28f));
    g.drawRoundedRectangle ({35.0f, 126.0f, 165.0f, 175.0f}, 7.0f, 1.0f);
    g.drawRoundedRectangle ({210.0f, 126.0f, 585.0f, 175.0f}, 7.0f, 1.0f);
    g.drawRoundedRectangle ({805.0f, 126.0f, 600.0f, 175.0f}, 7.0f, 1.0f);

    g.setColour (text);
    g.setFont (juce::FontOptions (13.0f, juce::Font::bold));
    g.drawText ("TEMPO", 48, 132, 135, 22, juce::Justification::centred);
    g.setFont (juce::FontOptions (10.0f, juce::Font::bold));
    g.drawText ("BPM", 48, 238, 135, 17, juce::Justification::centred);

    // Sequencer
    drawPanel ({12.0f, 330.0f, 1416.0f, 276.0f}, "SEQUENCER");
    g.setFont (juce::FontOptions (10.0f, juce::Font::bold));
    g.setColour (text.withAlpha (0.75f));
    g.drawText ("8 STEP ANALOG SEQUENCER", 1165, 342, 215, 18, juce::Justification::centredRight);

    const int active = processor.getCurrentStep();
    for (int i = 0; i < 8; ++i)
    {
        const int cx = 120 + i * 170;
        auto card = juce::Rectangle<float> ((float) cx - 82.0f, 378.0f, 164.0f, 205.0f);
        g.setColour (active == i ? knob.withAlpha (0.16f) : panel.darker (0.025f));
        g.fillRoundedRectangle (card, 7.0f);
        g.setColour (active == i ? knob : text.withAlpha (0.22f));
        g.drawRoundedRectangle (card, 7.0f, active == i ? 2.0f : 1.0f);

        g.setColour (juce::Colour::fromRGB (29,29,27));
        g.fillEllipse ((float) cx - 8.0f, 384.0f, 16.0f, 16.0f);
        if (active == i)
        {
            g.setColour (knob.withAlpha (0.28f));
            g.fillEllipse ((float) cx - 13.0f, 379.0f, 26.0f, 26.0f);
            g.setColour (knob.brighter (0.25f));
            g.fillEllipse ((float) cx - 7.0f, 385.0f, 14.0f, 14.0f);
        }
    }

    // Modulation, drive, transport
    drawPanel ({12.0f, 616.0f, 770.0f, 178.0f}, "MODULATION");
    drawPanel ({790.0f, 616.0f, 290.0f, 178.0f}, "DRIVE");
    drawPanel ({1090.0f, 616.0f, 338.0f, 178.0f}, "TRANSPORT");

    g.setColour (text);
    g.setFont (juce::FontOptions (10.0f, juce::Font::bold));
    g.drawText ("ANALOG SATURATION", 818, 742, 235, 18, juce::Justification::centred);
    g.drawText ("RUN", 1105, 748, 90, 18, juce::Justification::centred);
    g.drawText ("TRIG", 1210, 748, 90, 18, juce::Justification::centred);
    g.drawText ("RAND", 1315, 748, 90, 18, juce::Justification::centred);

    // Deep structure
    drawPanel ({12.0f, 806.0f, 1416.0f, 206.0f}, "DEEP / STRUCTURE");
    g.setFont (juce::FontOptions (9.5f, juce::Font::bold));
    g.setColour (text.withAlpha (0.72f));
    g.drawText ("ADVANCED TONAL SHAPING & CIRCUIT BEHAVIOUR",
                1000, 818, 375, 18, juce::Justification::centredRight);

    g.setFont (juce::FontOptions (8.8f));
    g.drawText ("OSC SHAPING • SUBHARMONICS • RING / CROSS MOD • WAVEFOLD • BODY RESONANCE • DUAL FILTER MORPH • DRIFT • WARMTH • WIDTH",
                50, 988, 1340, 16, juce::Justification::centred);

    g.setFont (juce::FontOptions (8.8f, juce::Font::bold));
    g.drawText ("3RDI AUDIO LABS • INTEL macOS VST3 / AU",
                1110, 1001, 280, 14, juce::Justification::centredRight);
}

juce::Colour ThreeRDIAnalogPercussionAudioProcessorEditor::getThemeColour
    (const juce::Identifier& property, juce::Colour fallback) const
{
    if (! processor.apvts.state.hasProperty (property))
        return fallback;

    return juce::Colour ((juce::uint32) (int64) processor.apvts.state.getProperty (property));
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::setThemeColour
    (const juce::Identifier& property, juce::Colour colour)
{
    processor.apvts.state.setProperty (property, (int64) colour.getARGB(), nullptr);
    applyThemeFromState (true);
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::applyThemeFromState (bool force)
{
    auto panel  = getThemeColour (panelColourProperty, defaultPanel);
    auto knob   = getThemeColour (knobColourProperty, defaultKnob);
    auto text   = getThemeColour (textColourProperty, defaultText);
    auto accent = getThemeColour (accentColourProperty, defaultAccent);

    int64 signature = ((int64) panel.getARGB() << 32)
                    ^ ((int64) knob.getARGB() << 1)
                    ^ ((int64) text.getARGB() << 17)
                    ^ (int64) accent.getARGB();

    if (! force && signature == lastThemeSignature)
        return;

    lastThemeSignature = signature;
    look.setTheme (panel, knob, text, accent);

    for (auto& s : knobs)
    {
        s->setColour (juce::Slider::textBoxTextColourId, text);
        s->setColour (juce::Slider::textBoxBackgroundColourId, juce::Colours::transparentBlack);
        s->setColour (juce::Slider::textBoxOutlineColourId, juce::Colours::transparentBlack);
    }

    for (auto& l : knobLabels)
        l->setColour (juce::Label::textColourId, text);

    driveSlider.setColour (juce::Slider::textBoxTextColourId, text);
    driveSlider.setColour (juce::Slider::textBoxOutlineColourId, juce::Colours::transparentBlack);

    auto configureButton = [text] (juce::TextButton& b)
    {
        b.setColour (juce::TextButton::textColourOffId, text);
        b.setColour (juce::TextButton::textColourOnId, text);
    };

    configureButton (themeButton);
    configureButton (saveButton);
    configureButton (loadButton);
    configureButton (prefsButton);
    configureButton (runButton);
    configureButton (trigButton);
    configureButton (randButton);
    for (auto& pad : stepPads)
        if (pad != nullptr)
        {
            pad->setColour (juce::TextButton::textColourOffId, juce::Colours::white);
            pad->setColour (juce::TextButton::textColourOnId, juce::Colours::white);
        }

    repaint();
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::showColourPicker
    (const juce::Identifier& property, juce::Colour current, juce::Component& anchor)
{
    auto selector = std::make_unique<CallbackColourSelector>
        (current, [this, property] (juce::Colour c) { setThemeColour (property, c); });

    juce::CallOutBox::launchAsynchronously (std::move (selector),
                                            anchor.getScreenBounds(),
                                            nullptr);
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::setThemePreset (int presetId)
{
    juce::Colour panel = defaultPanel;
    juce::Colour knob = defaultKnob;
    juce::Colour text = defaultText;
    juce::Colour accent = defaultAccent;

    switch (presetId)
    {
        case 1: break; // Original
        case 2: panel = juce::Colour::fromRGB (45,45,42); knob = juce::Colour::fromRGB (211,94,32); text = juce::Colours::white; accent = juce::Colour::fromRGB (130,55,18); break;
        case 3: panel = juce::Colour::fromRGB (35,39,20); knob = juce::Colour::fromRGB (190,255,0); text = juce::Colour::fromRGB (235,255,195); accent = juce::Colour::fromRGB (102,135,0); break;
        case 4: panel = juce::Colour::fromRGB (22,19,36); knob = juce::Colour::fromRGB (255,35,169); text = juce::Colour::fromRGB (80,235,255); accent = juce::Colour::fromRGB (92,45,210); break;
        case 5: panel = juce::Colour::fromRGB (48,10,12); knob = juce::Colour::fromRGB (235,38,38); text = juce::Colour::fromRGB (255,222,210); accent = juce::Colour::fromRGB (125,12,18); break;
        case 6: panel = juce::Colour::fromRGB (208,226,234); knob = juce::Colour::fromRGB (66,167,215); text = juce::Colour::fromRGB (16,38,52); accent = juce::Colour::fromRGB (35,105,142); break;
        case 7: panel = juce::Colour::fromRGB (26,31,18); knob = juce::Colour::fromRGB (110,255,0); text = juce::Colour::fromRGB (226,255,199); accent = juce::Colour::fromRGB (62,122,0); break;
        case 8: panel = juce::Colour::fromRGB (83,88,91); knob = juce::Colour::fromRGB (194,126,52); text = juce::Colour::fromRGB (245,245,238); accent = juce::Colour::fromRGB (42,45,47); break;
        case 9: panel = juce::Colour::fromRGB (38,21,54); knob = juce::Colour::fromRGB (179,84,255); text = juce::Colour::fromRGB (246,224,255); accent = juce::Colour::fromRGB (92,44,140); break;
        default: break;
    }

    processor.apvts.state.setProperty (panelColourProperty, (int64) panel.getARGB(), nullptr);
    processor.apvts.state.setProperty (knobColourProperty, (int64) knob.getARGB(), nullptr);
    processor.apvts.state.setProperty (textColourProperty, (int64) text.getARGB(), nullptr);
    processor.apvts.state.setProperty (accentColourProperty, (int64) accent.getARGB(), nullptr);
    applyThemeFromState (true);
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::showThemeMenu()
{
    juce::PopupMenu menu;
    menu.addSectionHeader ("THEME PRESETS");
    menu.addItem (1, "Original");
    menu.addItem (2, "Industrial");
    menu.addItem (3, "Acid");
    menu.addItem (4, "Cyberpunk");
    menu.addItem (5, "Blood Red");
    menu.addItem (6, "Ice");
    menu.addItem (7, "Toxic");
    menu.addItem (8, "Steel");
    menu.addItem (9, "Violet");

    menu.addSeparator();
    menu.addSectionHeader ("CUSTOM COLOURS");
    menu.addItem (101, "Panel colour...");
    menu.addItem (102, "Knob colour...");
    menu.addItem (103, "Text colour...");
    menu.addItem (104, "Accent colour...");
    menu.addSeparator();
    menu.addItem (200, "Reset complete patch + theme");

    menu.showMenuAsync (juce::PopupMenu::Options().withTargetComponent (themeButton),
        [this] (int result)
        {
            if (result >= 1 && result <= 9)
                setThemePreset (result);
            else if (result == 101)
                showColourPicker (panelColourProperty, getThemeColour (panelColourProperty, defaultPanel), themeButton);
            else if (result == 102)
                showColourPicker (knobColourProperty, getThemeColour (knobColourProperty, defaultKnob), themeButton);
            else if (result == 103)
                showColourPicker (textColourProperty, getThemeColour (textColourProperty, defaultText), themeButton);
            else if (result == 104)
                showColourPicker (accentColourProperty, getThemeColour (accentColourProperty, defaultAccent), themeButton);
            else if (result == 200)
                resetToDefaults();
        });
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::savePreset()
{
    auto presetDir = juce::File::getSpecialLocation (juce::File::userDocumentsDirectory)
                         .getChildFile ("3RDI Analog Percussion Presets");
    presetDir.createDirectory();

    presetChooser = std::make_unique<juce::FileChooser>
        ("Save 3RDI preset", presetDir.getChildFile ("My 3RDI Preset.3rdi"), "*.3rdi");

    presetChooser->launchAsync (juce::FileBrowserComponent::saveMode
                              | juce::FileBrowserComponent::canSelectFiles,
        [this] (const juce::FileChooser& chooser)
        {
            auto file = chooser.getResult();
            if (file == juce::File())
                return;

            if (! file.hasFileExtension ("3rdi"))
                file = file.withFileExtension ("3rdi");

            if (auto xml = processor.apvts.copyState().createXml())
                xml->writeTo (file);
        });
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::loadPreset()
{
    auto presetDir = juce::File::getSpecialLocation (juce::File::userDocumentsDirectory)
                         .getChildFile ("3RDI Analog Percussion Presets");
    presetDir.createDirectory();

    presetChooser = std::make_unique<juce::FileChooser>
        ("Load 3RDI preset", presetDir, "*.3rdi");

    presetChooser->launchAsync (juce::FileBrowserComponent::openMode
                              | juce::FileBrowserComponent::canSelectFiles,
        [this] (const juce::FileChooser& chooser)
        {
            auto file = chooser.getResult();
            if (! file.existsAsFile())
                return;

            auto xml = juce::XmlDocument::parse (file);
            if (xml != nullptr)
            {
                auto state = juce::ValueTree::fromXml (*xml);
                if (state.isValid())
                {
                    processor.apvts.replaceState (state);
                    applyThemeFromState (true);
                }
            }
        });
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::resetToDefaults()
{
    for (auto* param : processor.getParameters())
    {
        param->beginChangeGesture();
        param->setValueNotifyingHost (param->getDefaultValue());
        param->endChangeGesture();
    }

    setThemePreset (1);
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::resized()
{
    // Fixed 1440 x 1024 detailed hardware-style panel.
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::timerCallback()
{
    auto run = processor.apvts.getRawParameterValue ("run")->load() > 0.5f;
    runButton.setButtonText (run ? "STOP" : "RUN");

    const int active = processor.getCurrentStep();
    for (int i = 0; i < 8; ++i)
        if (stepPads[(size_t) i] != nullptr)
            stepPads[(size_t) i]->setToggleState (i == active, juce::dontSendNotification);

    applyThemeFromState();
    repaint (20, 330, 1400, 275);
}
