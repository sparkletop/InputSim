InputSimStrip {
	var <path, range, limitRange,
	streams, modes, mode,
	sliderSpec, slider, modeMenu,
	rangeSlider, numBox, <view;

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
			"Lo rand",
			"Hi rand"
		];

		limitRange = range;

		mode = modes.first;

		this.prInitStreams;

		modeMenu = PopUpMenu()
		.items_(modes)
		.value_(0)
		.action_({ | menu |
			rangeSlider.enabled_(menu.value > 0);
			mode = modes[menu.value];
		});

		slider = Slider()
		.action_({ | in |
			numBox.value = sliderSpec.map(in.value);
			streams[modes.first] = numBox.value;
		});

		rangeSlider = RangeSlider()
		.enabled_(false)
		.action_({ | slider |
			limitRange.start = sliderSpec.map(slider.lo);
			limitRange.end = sliderSpec.map(slider.hi);
			this.prInitStreams;
		});

		numBox = NumberBox()
		.action_({
			slider.value = sliderSpec.unmap(numBox.value);
			streams[modes.first] = numBox.value;
		})
		.align_(\center)
		.maxDecimals_(0)
		.clipLo_(0.01).clipHi_(range.end);

		this.prInitStreams;

		view = VLayout(
			StaticText().string_(path),
			HLayout(slider, rangeSlider),
			numBox,
			modeMenu
		);

		^this;
	}

	prInitStreams {
		streams = Dictionary.newFrom([
			modes,
			[
				range.start,
				Pwhite(limitRange.start, limitRange.end).asStream,
				Pbrown(limitRange.start, limitRange.end, (limitRange.end-limitRange.start) * 0.1).asStream,
				Pwhite().pow(4)
				.linlin(0,1,limitRange.start,limitRange.end-((limitRange.end - limitRange.start) * 0.25)).asStream,
				Pwhite().pow(4)
				.linlin(0,1,limitRange.end,limitRange.start+((limitRange.end - limitRange.start) * 0.25)).asStream
			]
		].lace);

	}

	prNext {
		numBox.valueAction_(streams[mode].next);
		^streams[modes.first];
	}
}

InputSim {
	var paths, min, max, waitMin, waitMax, targetAddr,
	strips, stripsPanel, <window, onOffBtn, mainLoop, waiter;

	*new {
		arg paths = ['/default'],
		min = 0, max = 127,
		waitMin = 0.01, waitMax = 2, targetAddr;
		^super.newCopyArgs(paths, min, max, waitMin, waitMax, targetAddr).init;
	}

	free {
		window.close;
	}

	init {
		/*window !? {
		strips.do({|s|s.stop});
		window.close;
		};*/

		if (paths.isKindOf(SequenceableCollection).not, {
			paths = [paths.asSymbol];
		});

		/*if (min.isKindOf(SequenceableCollection).not, {
			min = [min];
		});

		if (max.isKindOf(SequenceableCollection).not, {
			max = [max];
		});*/

		waiter = (
			spec: ControlSpec(waitMin, waitMax, 1.5),
			time: [waitMax, waitMin].mean.round(0.1)
		);

		waiter.knob = Knob()
		.action_({
			waiter.numBox.value = waiter.spec.map(waiter.knob.value);
			waiter.time = waiter.numBox.value;
		});

		waiter.numBox = NumberBox()
		.clipLo_(waitMin).clipHi_(waitMax)
		.maxDecimals_(2)
		.step_((waitMax-waitMin)*0.01)
		.scroll_step_((waitMax-waitMin)*0.01)
		.align_(\center)
		.maxWidth_(60)
		.action_({ |box|
			waiter.knob.value = waiter.spec.unmap(box.value);
			waiter.time = box.value;
		});

		waiter.numBox.valueAction_(waiter.time);

		//waiter.numBox.valueAction_(waiter.time);

		//waiter.spec = ControlSpec(waitMin, waitMax, 1.5);

		targetAddr = targetAddr ?? NetAddr.localAddr;

		//waiter.time = waiter.time ?? [waitMax, waitMin].mean.round(0.1);

		stripsPanel = HLayout();

		strips = paths.collect{ |path|
			var strip = InputSimStrip(path, Interval(min, max));
			stripsPanel.add(strip.view);
			strip;
		};

		mainLoop = Routine({
			{
				strips.do{ |strip|
					{
						targetAddr.sendMsg(strip.path, strip.prNext);
					}.defer;
				};
				waiter.time.wait;
			}.loop
		});

		// on/off button
		onOffBtn = Button().states_([
			["Off"],
			["On", Color.black, Color(0.5,1,0.5)]
		]).action_({ | button |
			if (button.value == 1,
				{mainLoop.reset.play},
				{mainLoop.stop},
			);
		});

		// wait time control panel
		/*waiter.numBox = NumberBox()
		.clipLo_(waitMin).clipHi_(waitMax)
		.maxDecimals_(2)
		.step_((waitMax-waitMin)*0.01)
		.scroll_step_((waitMax-waitMin)*0.01)
		.align_(\center)
		.maxWidth_(60)
		.action_({ |box|
			waiter.knob.value = waiter.spec.unmap(box.value);
			waiter.time = box.value;
		});

		waiter.knob = Knob()
		.action_({
			waiter.numBox.value = waiter.spec.map(waiter.knob.value);
			waiter.time = waiter.numBox.value;
		});

		waiter.numBox.valueAction_(waiter.time);
		*/

		// put the elements together
		window = Window.new(
			"InputSim",
			Rect(
				rrand(100,200),
				rrand(100,200),
				paths.size * 150,
				500)
		)
		.alwaysOnTop_(true)
		.layout_(VLayout(
			HLayout(
				onOffBtn,
				StaticText()
				.string_("Wait")
				.align_(\right),
				waiter.knob,
				waiter.numBox
			)
			.setStretch(1,10)
			.setStretch(0,4),
			stripsPanel
		))
		.onClose_({ mainLoop.stop })
		.front;
	}
}
