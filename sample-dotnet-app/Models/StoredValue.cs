using System;
using System.ComponentModel.DataAnnotations;

namespace sample_dotnet_app.Models {

    public class StoredValue {
        [Key]
        public long Id {get; set;}
        public string Value {get; set;}
        public DateTime Created {get; set;}
        public DateTime Updated {get; set;}
    }

}