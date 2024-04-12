use std::sync::Arc;
use async_std::task;
use jni::objects::{JByteArray, JClass, JObject, ReleaseMode};
use jni::sys::{jbyteArray, jobject, jstring};
use jni::JNIEnv;
use log::{LevelFilter, trace};
use up_client_android::UPClientAndroid;
use up_rust::{UAuthority, UEntity, UListener, UMessage, UStatus, UTransport, UUri};
use up_streamer::UStreamer;
use android_logger::Config;
use async_trait::async_trait;


use protobuf::Message;

#[derive(Clone)]
pub struct DummyListener;

#[async_trait]
impl UListener for DummyListener {
    async fn on_receive(&self, msg: UMessage) {
        trace!("Pinged from inside of DummyListener: message: {msg:?}");
    }

    async fn on_error(&self, err: UStatus) {
        todo!()
    }
}

// This keeps Rust from "mangling" the name and making it unique for this
// crate.
#[no_mangle]
pub extern "system" fn Java_org_eclipse_uprotocol_streamer_service_NativeBridge_initializeStreamer<
    'local,
>(
    mut env: JNIEnv<'local>,
    // This is the class that owns our static method. It's not going to be used,
    // but still must be present to match the expected signature of a static
    // native method.
    class: JClass<'local>,
    up_client: JObject,
    usub: JObject,
) -> jstring {

    android_logger::init_once(
        Config::default().with_max_level(LevelFilter::Trace),
    );

    log::info!("entered initializeStreamer");

    let ustreamer = UStreamer::new("AndroidStreamer", 100);
    let up_client_android = task::block_on(UPClientAndroid::new(&env, up_client, usub));

    let dummy_uuri = UUri {
        authority: Some(UAuthority {
            name: Some("foo_authority".to_string()),
            number: None,
            ..Default::default()
        }).into(),
        entity: Some(UEntity {
            name: "bar_entity".to_string(),
            version_major: Some(1),
            ..Default::default()
        }).into(),
        ..Default::default()
    };

    let dummy_listener = Arc::new(DummyListener);
    let register_res = task::block_on(up_client_android.register_listener(dummy_uuri, dummy_listener));

    // let up_client_zenoh = ...;

    let empty_string = "";
    let mock_string = "mock_string";
    let status_strings = vec![empty_string, mock_string];
    let status_string = status_strings.join("\n");

    // Then we have to create a new Java string to return. Again, more info
    // in the `strings` module.
    let output = env
        .new_string(status_string)
        .expect("Couldn't create java string!");

    log::info!("exiting initializeStreamer");

    // Finally, extract the raw pointer to return.
    output.into_raw()
}

// This keeps Rust from "mangling" the name and making it unique for this
// crate.
#[no_mangle]
pub extern "system" fn Java_org_eclipse_uprotocol_streamer_service_NativeBridge_teardownStreamer<
    'local,
>(
    mut env: JNIEnv<'local>,
    // This is the class that owns our static method. It's not going to be used,
    // but still must be present to match the expected signature of a static
    // native method.
    class: JClass<'local>,
) -> jstring {


    let empty_string = "";
    let status_strings = vec![empty_string];
    let status_string = status_strings.join("\n");

    // Then we have to create a new Java string to return. Again, more info
    // in the `strings` module.
    let output = env
        .new_string(status_string)
        .expect("Couldn't create java string!");

    // Finally, extract the raw pointer to return.
    output.into_raw()
}

