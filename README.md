# RxPM
Reactive implementation of [Presentation Model](https://martinfowler.com/eaaDev/PresentationModel.html) pattern in Android.

RxPM allows to use the RxJava all the way from the view to the model.  
The main advantage of that is the **ability to write UI logic declaratively**.

We focus on practice, so the library solves most of the typical presentation layer problems.

### Why PM and not MVVM?
Actually the only difference between these two is that PM does'n have automated binding.  
So PM name is just more correct for us. However many call it MVVM, so let it be.

### The Diagram
<img src="/docs/images/rxpm_diagram.png">

## Usage

Add the dependency to your build.gradle:
```gradle
dependencies {

    implementation 'me.dmdev.rxpm:rxpm:1.2'
    
    // RxBinding (optional)
    implementation 'com.jakewharton.rxbinding2:rxbinding-kotlin:$latest_version'
    
    // Conductor (if you use it)
    implementation 'com.bluelinelabs:conductor:$latest_version'
}
```

### Create a Presentation Model class and define reactive properties
```kotlin
class DataPresentationModel(
    private val dataModel: DataModel
) : PresentationModel() {

    val data = State<List<Item>>(emptyList())
    val inProgress = State(false)
    val errorMessage = Command<String>()
    val refreshAction = Action<Unit>()

    override fun onCreate() {
        super.onCreate()

        refreshAction.observable
            .skipWhileInProgress(inProgress.observable)
            .flatMapSingle {
                dataModel.loadData()
                    .subscribeOn(Schedulers.io())
                    .bindProgress(inProgress.consumer)
                    .doOnError { 
                        errorMessage.consumer.accept("Loading data error")
                    }
            }
            .retry()
            .subscribe(data.consumer)
            .untilDestroy()

        refreshAction.consumer.accept(Unit) // first loading on create
    }
}
```
### Bind to the PresentationModel properties
```kotlin
class DataFragment : PmSupportFragment<DataPresentationModel>() {

    override fun providePresentationModel() = DataPresentationModel(DataModel())

    override fun onBindPresentationModel(pm: DataPresentationModel) {

        pm.inProgress.observable bindTo swipeRefreshLayout.refreshing()

        pm.data.observable bindTo {
            // adapter.setItems(it)
        }

        pm.errorMessage.observable bindTo {
            // show Snackbar
        }

        swipeRefreshLayout.refreshes() bindTo pm.refreshAction.consumer
    }
}
```

## Main Components

### PresentationModel
The PresentationModel stores the state of the View and holds the UI logic.  
PresentationModel instance is automatically retained during configuration changes. This behavior is provided by the delegate which controls the lifecycle.

Lifecycle callbacks:
- `onCreate()` — Called when the PresentationModel is created. Initialize your Rx chains in this method.
- `onBind()` — Called when the View binds to the PresentationModel.
- `onUnbind()` — Called when the View unbinds from the PresentationModel.
- `onDestroy()` — Called when the PresentationModel is being destroyed. Dispose all subscriptions in this method.

What's more you can observe lifecycle changes via `lifecycleObservable`.

Also the useful extensions of the *Disposable* are available to make lifecycle handling easier: `untilUnbind` and `untilDestroy`. 

### PmView
The library has several predefined PmView implementations: `PmSupportActivity`, `PmSupportFragment` and `PmController` (for [Conductor](https://github.com/bluelinelabs/Conductor/)'s users).  

You have to implement only two methods:
1) `providePresentationModel()` — Create the instance of the PresentationModel.
2) `onBindPresentationModel()` — Bind to the PresentationModel properties in this method. Use the `bindTo` extension and [RxBinding](https://github.com/JakeWharton/RxBinding) for this. 

Also there is variants of these with Google Map integration.

### State
**State** is a reactive property which represents a View state.  
It holds the latest value and emits it on binding. For example, **State** can be used to represent a progress of the http-request or some data that can change in time.

In the PresentationModel:
```kotlin
val inProgress = State<Boolean>(false)
```
Change the value through the consumer:
```kotlin
inProgress.consumer.accept(true)
```
Observe changes in the View:
```kotlin
pm.inProgress.observable bindTo progressBar.visibility()
```

### Action
**Action** is the reactive property which represents the user actions.  
It's mostly used for receiving events from the View, such as clicks.

In the View:
```kotlin
button.clicks() bindTo pm.buttonClicks.consumer
```

In the PresentationModel:
```kotlin
val buttonClicks = Action<Unit>()

buttonClicks.observable
    .subscribe {
        // handle click
    }
    .untilDestroy()
```

### Command
**Command** is the reactive property which represents a command to the View.  
It can be used to show a toast or alert dialog.

Define it in the PresentationModel:
```kotlin
val errorMessage = Command<String>()
```
Show some message in the View:
```kotlin
pm.errorMessage.observable bindTo { message ->
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
```

When the View is unbound from the PresentationModel, **Command** collects all received values and emits them on binding:

![Command](/docs/images/bwu.png)

## Controls

### Two-way Data Binding
For the cases of two-way data binding (eg. input field text changes) the library has predefined [Сontrols](https://github.com/dmdevgo/RxPM/tree/develop/rxpm/src/main/kotlin/me/dmdev/rxpm/widget).

In the PresentationModel:
```kotlin
val name = inputControl(
    formatter = {
        it.take(50).capitalize().replace("[^a-zA-Z- ]".toRegex(), "")
    }
)

val checked = checkControl()
```

In the View:
```kotlin
pm.name bindTo editText
pm.checked bindTo checkBox
```

### Dialogs

The DialogControl is a component make possible the interaction with the dialogs in reactive style.  
It manages the lifecycle and the state of the dialog. Just bind your Dialog object (eg. AlertDialog) to the DialogControl. No need in DialogFragment anymore.

Here is an example of the dialog to confirm exit from the application:
```kotlin
enum class DialogResult { EXIT, CANCEL }

val dialogControl = dialogControl<String, DialogResult>()

val backButtonClicks = Action<Unit>()

backButtonClicks.observable
    .switchMapMaybe {
        dialogControl.showForResult("Do you really want to exit?")
    }
    .filter { it == DialogResult.EXIT }
    .subscribe {
        // close application
    }
    .untilDestroy()
```

Bind the `dialogControl` to AlertDialog in the View:
```kotlin
pm.dialogControl bindTo { message, dialogControl ->
    AlertDialog.Builder(context)
        .setMessage(message)
        .setPositiveButton("Exit") { _, _ ->
            dialogControl.sendResult(DialogResult.EXIT)
        }
        .setNegativeButton("Cancel") { _, _ ->
            dialogControl.sendResult(DialogResult.CANCEL)
        }
        .create()
}
```

## Sample

The [sample](https://github.com/dmdevgo/RxPM/tree/develop/sample) shows how to use RxPM in practice.

## How to test PM?

You can test PresentationModel in the same way as any other class with RxJava (using TestObserver, Mockito, other).  
The only difference is that you have to change it's lifecycle state while testing. And **PmTestHelper** allows you to do that.

Note that Command passes events only when PM is in the BINDED state.

## License

```

MIT License

Copyright (c) 2017 Dmitriy Gorbunov (dmitriy.goto@gmail.com)
                   and Vasili Chyrvon (vasili.chyrvon@gmail.com)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
