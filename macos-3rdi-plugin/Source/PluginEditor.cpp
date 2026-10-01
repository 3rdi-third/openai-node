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
    auto diameter = (float) juce::jmin (width, height) - 8.0f;
    auto radius = diameter * 0.5f;
    auto centreX = (float) x + (float) width * 0.5f;
    auto centreY = (float) y + (float) height * 0.5f;
    auto r = juce::Rectangle<float> (centreX - radius, centreY - radius, diameter, diameter);

    g.setColour (knobColour);
    g.fillEllipse (r);
    g.setColour (accentColour);
    g.fillEllipse (r.reduced (radius * 0.31f));

    auto angle = rotaryStartAngle + sliderPos * (rotaryEndAngle - rotaryStartAngle);
    juce::Path p;
    auto pointerLength = radius * 0.56f;
    auto pointerThickness = juce::jmax (2.0f, radius * 0.09f);
    p.addRoundedRectangle (-pointerThickness * 0.5f, -radius * 0.12f,
                           pointerThickness, pointerLength, pointerThickness * 0.5f);
    p.applyTransform (juce::AffineTransform::rotation (angle)
                                      .translated (centreX, centreY));
    g.setColour (textColour);
    g.fillPath (p);
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::AnalogLookAndFeel::drawButtonBackground
    (juce::Graphics& g, juce::Button& b, const juce::Colour&, bool over, bool down)
{
    auto r = b.getLocalBounds().toFloat().reduced (1.0f);
    auto fill = b.getToggleState() ? knobColour : panelColour;
    if (over) fill = fill.brighter (0.05f);
    if (down) fill = fill.darker (0.08f);
    g.setColour (fill);
    g.fillRoundedRectangle (r, 8.0f);
    g.setColour (accentColour);
    g.drawRoundedRectangle (r, 8.0f, 2.0f);
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::AnalogLookAndFeel::drawLinearSlider
    (juce::Graphics& g, int x, int y, int width, int height, float sliderPos,
     float, float, juce::Slider::SliderStyle style, juce::Slider& slider)
{
    auto r = juce::Rectangle<float> ((float) x, (float) y, (float) width, (float) height).reduced (1.0f);

    if (slider.getName() == "TEMPO")
    {
        g.setColour (panelColour);
        g.fillRoundedRectangle (r, 8.0f);
        g.setColour (accentColour);
        g.drawRoundedRectangle (r, 8.0f, 2.5f);
        g.setColour (textColour);
        g.setFont (juce::FontOptions (26.0f, juce::Font::bold));
        g.drawFittedText (juce::String (slider.getValue(), 1), r.toNearestInt(),
                          juce::Justification::centred, 1);
        return;
    }

    if (style == juce::Slider::LinearHorizontal)
    {
        auto bar = r.withHeight (19.0f).withCentre ({ r.getCentreX(), r.getCentreY() });
        g.setColour (panelColour.darker (0.18f));
        g.fillRoundedRectangle (bar, 9.0f);
        auto filled = bar.withRight (sliderPos);
        g.setColour (knobColour);
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
    setSize (1200, 850);

    if (! processor.apvts.state.hasProperty (panelColourProperty))
        processor.apvts.state.setProperty (panelColourProperty, (int64) defaultPanel.getARGB(), nullptr);
    if (! processor.apvts.state.hasProperty (knobColourProperty))
        processor.apvts.state.setProperty (knobColourProperty, (int64) defaultKnob.getARGB(), nullptr);
    if (! processor.apvts.state.hasProperty (textColourProperty))
        processor.apvts.state.setProperty (textColourProperty, (int64) defaultText.getARGB(), nullptr);
    if (! processor.apvts.state.hasProperty (accentColourProperty))
        processor.apvts.state.setProperty (accentColourProperty, (int64) defaultAccent.getARGB(), nullptr);

    applyThemeFromState (true);

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

    auto deepX = std::array<int, 14> { 72, 152, 232, 312, 392, 472, 552,
                                       632, 712, 792, 872, 952, 1032, 1112 };
    addKnob ("shape1",      "SHAPE 1",   {deepX[0]-27, 752, 54, 68}, "", 2, true);
    addKnob ("shape2",      "SHAPE 2",   {deepX[1]-27, 752, 54, 68}, "", 2, true);
    addKnob ("subLevel",    "SUB",       {deepX[2]-27, 752, 54, 68}, "", 2, true);
    addKnob ("ringMod",     "RING",      {deepX[3]-27, 752, 54, 68}, "", 2, true);
    addKnob ("crossMod",    "X-MOD",     {deepX[4]-27, 752, 54, 68}, "", 2, true);
    addKnob ("wavefold",    "FOLD",      {deepX[5]-27, 752, 54, 68}, "", 2, true);
    addKnob ("bodyLevel",   "BODY",      {deepX[6]-27, 752, 54, 68}, "", 2, true);
    addKnob ("bodyTune",    "BODY TUNE", {deepX[7]-27, 752, 54, 68}, " st", 0, true);
    addKnob ("bodyDecay",   "BODY DEC",  {deepX[8]-27, 752, 54, 68}, " ms", 0, true);
    addKnob ("filterMorph", "FLT MORPH", {deepX[9]-27, 752, 54, 68}, "", 2, true);
    addKnob ("filterDrive", "FLT DRIVE", {deepX[10]-27,752, 54, 68}, "", 2, true);
    addKnob ("drift",       "DRIFT",     {deepX[11]-27,752, 54, 68}, "", 2, true);
    addKnob ("warmth",      "WARMTH",    {deepX[12]-27,752, 54, 68}, "", 2, true);
    addKnob ("stereoWidth", "WIDTH",     {deepX[13]-27,752, 54, 68}, "", 2, true);

    tempoSlider.setName ("TEMPO");
    tempoSlider.setSliderStyle (juce::Slider::LinearBar);
    tempoSlider.setTextBoxStyle (juce::Slider::NoTextBox, false, 0, 0);
    tempoSlider.setBounds (58, 143, 102, 56);
    addAndMakeVisible (tempoSlider);
    tempoAttachment = std::make_unique<SliderAttachment> (processor.apvts, "tempo", tempoSlider);

    driveSlider.setSliderStyle (juce::Slider::LinearHorizontal);
    driveSlider.setTextBoxStyle (juce::Slider::TextBoxRight, false, 48, 20);
    driveSlider.setBounds (68, 598, 290, 34);
    driveSlider.setColour (juce::Slider::textBoxTextColourId, look.textColour);
    driveSlider.setColour (juce::Slider::textBoxOutlineColourId, juce::Colours::transparentBlack);
    addAndMakeVisible (driveSlider);
    driveAttachment = std::make_unique<SliderAttachment> (processor.apvts, "drive", driveSlider);

    themeButton.setBounds (776, 58, 88, 43);
    themeButton.onClick = [this] { showThemeMenu(); };
    addAndMakeVisible (themeButton);

    saveButton.setBounds (870, 58, 82, 43);
    saveButton.onClick = [this] { savePreset(); };
    addAndMakeVisible (saveButton);

    loadButton.setBounds (958, 58, 82, 43);
    loadButton.onClick = [this] { loadPreset(); };
    addAndMakeVisible (loadButton);

    prefsButton.setClickingTogglesState (true);
    prefsButton.setBounds (1046, 58, 126, 43);
    addAndMakeVisible (prefsButton);
    hostSyncAttachment = std::make_unique<ButtonAttachment> (processor.apvts, "hostSync", prefsButton);

    runButton.setClickingTogglesState (true);
    runButton.setBounds (890, 618, 95, 53);
    runButton.setColour (juce::TextButton::textColourOffId, look.textColour);
    runButton.setColour (juce::TextButton::textColourOnId, look.textColour);
    addAndMakeVisible (runButton);
    runAttachment = std::make_unique<ButtonAttachment> (processor.apvts, "run", runButton);

    trigButton.setBounds (995, 618, 85, 53);
    trigButton.setColour (juce::TextButton::textColourOffId, look.textColour);
    trigButton.onClick = [this] { processor.manualTrigger(); };
    addAndMakeVisible (trigButton);

    randButton.setBounds (1090, 618, 82, 53);
    randButton.setColour (juce::TextButton::textColourOffId, look.textColour);
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
    s->setColour (juce::Slider::textBoxTextColourId, look.textColour);
    s->setColour (juce::Slider::textBoxBackgroundColourId, juce::Colours::transparentBlack);
    s->setColour (juce::Slider::textBoxOutlineColourId, juce::Colours::transparentBlack);
    s->setBounds (bounds);

    auto label = std::make_unique<juce::Label>();
    label->setText (labelText, juce::dontSendNotification);
    label->setJustificationType (juce::Justification::centred);
    label->setFont (juce::FontOptions (small ? 9.5f : 11.5f, juce::Font::bold));
    label->setColour (juce::Label::textColourId, look.textColour);
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
    g.fillAll (look.panelColour.darker (0.18f));

    g.setColour (look.panelColour);
    g.fillRoundedRectangle ({14.0f, 14.0f, 1172.0f, 822.0f}, 22.0f);
    g.setColour (look.panelColour.brighter (0.08f));
    g.fillRoundedRectangle ({28.0f, 28.0f, 1144.0f, 794.0f}, 17.0f);

    g.setColour (look.textColour);
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

    g.setColour (look.textColour.withAlpha (0.28f));
    g.fillRect (62, 251, 1076, 2);

    const int active = processor.getCurrentStep();
    for (int i = 0; i < 8; ++i)
    {
        int x = 112 + i * 139;
        g.setColour (i == active ? look.knobColour : look.textColour);
        if (i == active)
            g.fillEllipse ((float) x - 9.0f, 277.0f, 18.0f, 18.0f);
        else
            g.drawEllipse ((float) x - 7.0f, 279.0f, 14.0f, 14.0f, 2.0f);

        g.setColour (look.textColour);
        g.setFont (juce::FontOptions (13.0f, juce::Font::bold));
        g.drawText (juce::String (i + 1), x - 20, 304, 40, 20, juce::Justification::centred);
    }

    g.setColour (look.textColour.withAlpha (0.25f));
    g.fillRect (62, 548, 1076, 2);

    g.setColour (look.textColour);
    g.setFont (juce::FontOptions (11.5f, juce::Font::bold));
    g.drawText ("DRIVE", 68, 572, 100, 18, juce::Justification::centredLeft);

    g.setColour (look.textColour.withAlpha (0.25f));
    g.fillRect (62, 704, 1076, 2);

    g.setColour (look.textColour);
    g.setFont (juce::FontOptions (11.5f, juce::Font::bold));
    g.drawText ("DEEP / STRUCTURE",
                68, 714, 260, 18, juce::Justification::centredLeft);
    g.setFont (juce::FontOptions (8.8f));
    g.drawText ("OSC SHAPING • SUB • RING/X-MOD • WAVEFOLD • BODY • DUAL FILTER • DRIFT • WARMTH • STEREO",
                250, 714, 890, 18, juce::Justification::centredRight);

    g.setFont (juce::FontOptions (9.0f));
    g.drawText ("3RDI AUDIO LABS • INTEL macOS VST3 / AU • DEEP ENGINE",
                820, 828, 352, 16, juce::Justification::centredRight);
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
    // Fixed 1200 x 850 layout: Android-style panel plus DEEP / STRUCTURE engine row.
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::timerCallback()
{
    auto run = processor.apvts.getRawParameterValue ("run")->load() > 0.5f;
    runButton.setButtonText (run ? "STOP" : "RUN");
    applyThemeFromState();
    repaint (80, 270, 1090, 60);
}
