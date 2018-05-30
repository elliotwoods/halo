import com.google.gson.Gson
import io.improbable.keanu.algorithms.variational.GradientOptimizer
import io.improbable.keanu.network.BayesNet
import io.improbable.keanu.vertices.dbl.nonprobabilistic.ConstantDoubleVertex
import io.improbable.keanu.vertices.dbl.probabilistic.GaussianVertex
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.FileReader

class HelioStatCalibration : ArrayList<HelioStatCalibration.DataPoint> {
    class DataPoint(val ABC : Vector3D, val control: ServoSetting) {}

    val helioStat = HelioStat(ProbabilisticVector3D(
            GaussianVertex(0.0, 100.0),
            GaussianVertex(0.0, 100.0),
            GaussianVertex(0.0, 100.0)
    ))

    constructor(array : List<DataPoint>) : super(array) {}
    constructor() : super() {}

    fun readFromFile(filename: String) {
        val gson = Gson()
        var buff = BufferedReader(FileReader(filename))

        val data = gson.fromJson(buff, CalibrationRawData::class.java)

        for(entry in data) {
            println(entry.key)
            println(entry.value.servoPositions)
            println(entry.value.data.plane.ABCD.split(",").map(String::toDouble))
            val abcd = entry.value.data.plane.ABCD.split(",").map(String::toDouble)
            val plane = Vector3D(abcd[0], abcd[1], abcd[2]).normalize().scalarMultiply(abcd[3])
            add(
                    DataPoint(
                            plane,
                            ServoSetting(entry.value.servoPositions["209"]?:0, entry.value.servoPositions["210"]?:0)
                    )
            )
        }

    }

    fun createBayesNet() : BayesNet {
        for(dataPoint in this) {
            val plane = helioStat.computeHeliostatPlane(
                    ConstantDoubleVertex(dataPoint.control.pitch.toDouble()),
                    ConstantDoubleVertex(dataPoint.control.rotation.toDouble())
            )
            plane.noisyObserve(dataPoint.ABC, Vector3D(0.01, 0.01, 0.01))
        }
        return BayesNet(helioStat.cPitch.connectedGraph)
    }

    fun inferMaxAPosteriori() {
        val model = createBayesNet()
        val optimiser = GradientOptimizer(model)
        optimiser.maxAPosteriori(10000)
    }

    fun calculateResiduals() {
        for(dataPoint in this) {
            val plane = helioStat.computeHeliostatPlane(
                    ConstantDoubleVertex(dataPoint.control.pitch.toDouble()),
                    ConstantDoubleVertex(dataPoint.control.rotation.toDouble())
            )
            val modelledPlane = Vector3D(plane.x.value, plane.y.value, plane.z.value)
            modelledPlane.subtract(dataPoint.ABC)
        }
    }

}