InputSimStrip {
	var <path, range, streams, modes, currentMode,
	sliderSpec, slider, modeMenu,
	rangeSlider, numBox, <panel, rangeText;

	*new {
		arg path, range;
		^super.newCopyArgs(path, range).init;
	}

	init {
		sliderSpec = ControlSpec(range.start, range.end, \lin, 1);

		modes = [
			"Static",
			"White noise",
			"Brown noise",
			"Low rand",
			"High rand",
			"Triangle",
			"Gaussian"
		];

		currentMode = modes.first;

		modeMenu = PopUpMenu()
		.items_(modes)
		.value_(0)
		.action_({ | menu |
			rangeSlider.enabled_(menu.value > 0);
			currentMode = modes[menu.value];
		});

		slider = Slider()
		.action_({ | in |
			numBox.value = sliderSpec.map(in.value);
			streams[modes.first] = numBox.value;
		});

		rangeSlider = RangeSlider()
		.enabled_(false)
		.action_({ | slider |
			var lo = sliderSpec.map(slider.lo),
			hi = sliderSpec.map(slider.hi);
			this.prInitStreams(lo, hi);
			rangeText.string_(lo.asInteger.asString ++ ":" ++ hi.asInteger)
		});

		rangeText = StaticText()
		.string_(range.start.asString ++ ":" ++ range.end);

		numBox = NumberBox()
		.action_({
			slider.value = sliderSpec.unmap(numBox.value);
			streams[modes.first] = numBox.value;
		})
		.align_(\center)
		.maxDecimals_(0)
		.clipLo_(range.start).clipHi_(range.end);

		this.prInitStreams(range.start, range.end);

		panel = VLayout(
			StaticText().string_("OSC path:" + path),
			HLayout(slider, rangeSlider),
			HLayout(
				numBox,
				rangeText
			),
			modeMenu
		);

		^this;
	}

	prInitStreams {
		arg lo, hi;
		streams = Dictionary.newFrom([
			modes,
			[
				range.start,
				Pwhite(lo, hi).asStream,
				Pbrown(lo, hi, (hi-lo) * 0.1).asStream,
				Pwhite().pow(4).linlin(0,1,lo,hi-((hi - lo) * 0.25)).asStream,
				Pwhite().pow(4).linlin(0,1,hi,lo+((hi - lo) * 0.25)).asStream,
				Pseq(Array.interpolation(50, lo, hi).mirror1).repeat.asStream,
				Pgauss([lo, hi].mean, (hi-lo) / 6).asStream
			]
		].lace);
	}

	prNext {
		numBox.valueAction_(streams[currentMode].next);
		^streams[modes.first];
	}
}


InputSim {
	var paths, min, max, targetAddr, strips, stripsPanel,
	window, updater;

	*new {
		arg paths = ['/default'],
		min = 0, max = 127, targetAddr;
		^super.newCopyArgs(paths, min, max, targetAddr).init;
	}

	init {
		// Default values
		if (paths.isKindOf(SequenceableCollection).not, {
			paths = [paths.asSymbol];
		});

		targetAddr = targetAddr ?? NetAddr.localAddr;

		updater = (
			spec: ControlSpec(1, 50, 2, 1, units: "Hz"),
			freq: 2
		);

		// Initialize all strips
		stripsPanel = HLayout();

		strips = paths.collect{ |path|
			var strip = InputSimStrip(path, Interval(min, max));
			stripsPanel.add(strip.panel);
			strip;
		};

		// Set up updater
		updater.knob = Knob()
		.action_({
			updater.numBox.value = updater.spec.map(updater.knob.value);
			updater.freq = updater.numBox.value;
		});

		updater.numBox = NumberBox()
		.clipLo_(updater.spec.minval)
		.clipHi_(updater.spec.maxval)
		.maxDecimals_(2)
		.step_((updater.spec.maxval-updater.spec.minval)*0.01)
		.scroll_step_((updater.spec.maxval-updater.spec.minval)*0.01)
		.align_(\center)
		.maxWidth_(60)
		.action_({ |box|
			updater.knob.value = updater.spec.unmap(box.value);
			updater.freq = box.value;
		});

		updater.numBox.valueAction_(updater.freq);

		updater.routine = Routine({
			{
				strips.do{ |strip|
					{
						targetAddr.sendMsg(strip.path, strip.prNext);
					}.defer;
				};
				updater.freq.reciprocal.wait;
			}.loop
		});

		updater.onOffBtn = Button().states_([
			["Off"],
			["On", Color.black, Color(0.5, 1, 0.5)]
		]).action_({ | button |
			if (button.value == 1,
				{ updater.routine.reset.play },
				{ updater.routine.stop },
			);
		});

		updater.panel = HLayout(
			updater.onOffBtn,
			/*StaticText()
			.string_("Rate")
			.align_(\right),*/
			updater.numBox,
			updater.knob,
		)
		.setStretch(1,10)
		.setStretch(0,4);

		// Put the GUI together
		window = Window.new(
			"InputSim",
			Rect(
				rrand(100,200),
				rrand(100,200),
				paths.size * 150,
				500
			)
		)
		.alwaysOnTop_(true)
		.layout_(VLayout(
			updater.panel,
			stripsPanel
		))
		.onClose_({ updater.routine.stop; })
		.front;
	}
}
