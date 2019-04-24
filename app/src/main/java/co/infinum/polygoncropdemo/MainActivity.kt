package co.infinum.polygoncropdemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.TypedValue
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), SeekBar.OnSeekBarChangeListener {

    private var sides = 3
    private var angle = 0
    private var radius = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initUI()
        setImage()
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        when (seekBar?.id) {
            R.id.seekBarSides -> sides = progress
            R.id.seekBarRotation -> angle = progress
            R.id.seekBarRadius -> radius = progress
        }
        setValues()
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {

    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        setImage()
    }

    private fun initUI() {
        seekBarSides.setOnSeekBarChangeListener(this)
        seekBarRotation.setOnSeekBarChangeListener(this)
        seekBarRadius.setOnSeekBarChangeListener(this)

        setValues()
    }

    private fun setImage() {
        Glide.with(image.context.applicationContext)
            .load("https://picsum.photos/id/0/800/800")
            .apply(
                RequestOptions()
                    .downsample(DownsampleStrategy.CENTER_INSIDE)
                    .transform(
                        PolygonCropTransformation(
                            sides,
                            angle,
                            Math.round(
                                TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_DIP,
                                    radius.toFloat(),
                                    applicationContext.resources.displayMetrics
                                )
                            )
                        )
                    )
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .signature(ObjectKey("${sides}_${angle}_$radius"))
            )
            .into(image)
    }

    private fun setValues() {
        valueSides.text = sides.toString()
        valueRotation.text = angle.toString()
        valueRadius.text = radius.toString()
    }
}
